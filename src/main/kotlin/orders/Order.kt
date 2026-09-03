package orders

interface PriceCalculator {
    fun calculateSubtotal(): Long
}

class Order(
    val id: Int,
    private val eventListener: ((OrderEvent) -> Unit)? = null
) : PriceCalculator {

    var status: OrderStatus = OrderStatus.Created
        private set

    // LinkedHashMap хранит позиции в порядке добавления: изменение количества
    // оставляет позицию на месте, а повторное добавление после удаления —
    // переносит в конец.
    private val items = LinkedHashMap<Int, OrderItem>()
    private val events = mutableListOf<OrderEvent>()

    init {
        require(id > 0) { "id заказа должен быть положительным, получено $id" }
    }

    fun getItems(): List<OrderItem> = items.values.toList()

    fun getEvents(): List<OrderEvent> = events.toList()

    fun addProduct(product: Product, quantity: Int = 1) {
        requireEditable()
        require(quantity > 0) { "количество должно быть положительным, получено $quantity" }

        val existing = items[product.id]
        val resultingQuantity = if (existing == null) {
            quantity
        } else {
            require(existing.product == product) {
                "в заказе уже есть другой товар с id ${product.id}"
            }
            Math.addExact(existing.quantity, quantity)
        }

        // existing.copy сохраняет исходный экземпляр товара, а не переданный.
        items[product.id] = existing?.copy(quantity = resultingQuantity) ?: OrderItem(product, quantity)
        emit(OrderEvent.ProductAdded(product.id, quantity, resultingQuantity))
    }

    fun changeQuantity(productId: Int, newQuantity: Int) {
        requireEditable()
        val item = requireItem(productId)
        require(newQuantity > 0) { "количество должно быть положительным, получено $newQuantity" }

        items[productId] = item.copy(quantity = newQuantity)
        emit(OrderEvent.QuantityChanged(productId, item.quantity, newQuantity))
    }

    fun removeProduct(productId: Int): OrderItem {
        requireEditable()
        val item = requireItem(productId)

        items.remove(productId)
        emit(OrderEvent.ProductRemoved(productId, item.quantity))
        return item
    }

    fun clear() {
        requireEditable()
        items.clear()
        emit(OrderEvent.OrderCleared)
    }

    fun findItem(productId: Int): OrderItem? = items[productId]

    override fun calculateSubtotal(): Long =
        items.values.fold(0L) { sum, item -> Math.addExact(sum, item.subtotal()) }

    fun confirm(
        discount: Discount? = null,
        discountLogger: ((String) -> Unit)? = null
    ) {
        check(status is OrderStatus.Created) { "подтвердить можно только заказ в статусе CREATED" }
        check(items.isNotEmpty()) { "нельзя подтвердить пустой заказ" }

        // Обе величины считаются до изменения состояния: если что-то упадёт,
        // заказ должен остаться нетронутым, без события в логе.
        val itemCount = items.values.countUnits()
        val price = calculatePrice(discount, discountLogger)

        status = OrderStatus.Confirmed(price, itemCount)
        emit(OrderEvent.OrderConfirmed(price))
    }

    fun reopen() {
        check(status is OrderStatus.Confirmed) { "вернуть в редактирование можно только подтверждённый заказ" }
        status = OrderStatus.Created
        emit(OrderEvent.OrderReopened)
    }

    fun pay(transactionId: String) {
        val confirmed = status as? OrderStatus.Confirmed
            ?: throw IllegalStateException("оплатить можно только подтверждённый заказ")
        require(transactionId.isNotBlank()) { "идентификатор транзакции не может быть пустым" }

        val paidPrice = confirmed.price.total
        status = OrderStatus.Paid(transactionId, paidPrice)
        emit(OrderEvent.OrderPaid(transactionId, paidPrice))
    }

    fun cancel(reason: String) {
        check(status is OrderStatus.Created || status is OrderStatus.Confirmed) {
            "отменить можно только заказ в статусе CREATED или CONFIRMED"
        }
        require(reason.isNotBlank()) { "причина отмены не может быть пустой" }

        val previousStatus = statusTypeOf(status)
        status = OrderStatus.Cancelled(reason, previousStatus)
        emit(OrderEvent.OrderCancelled(reason, previousStatus))
    }

    private fun requireEditable() = check(status is OrderStatus.Created) {
        "редактировать можно только заказ в статусе CREATED"
    }

    private fun requireItem(productId: Int): OrderItem =
        items[productId] ?: throw NoSuchElementException("в заказе нет товара с id $productId")

    private fun emit(event: OrderEvent) {
        events += event
        eventListener?.invoke(event)
    }
}
