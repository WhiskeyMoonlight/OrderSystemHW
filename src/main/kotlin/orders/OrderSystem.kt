package orders

class OrderSystem(
    val catalog: ProductCatalog = ProductCatalog()
) {

    private val orders = LinkedHashMap<Int, Order>()

    fun createOrder(
        orderId: Int,
        eventListener: ((OrderEvent) -> Unit)? = null
    ): Order {
        require(!orders.containsKey(orderId)) { "заказ с id $orderId уже существует" }

        val order = Order(orderId, eventListener)
        orders[orderId] = order
        return order
    }

    fun getOrder(orderId: Int): Order? = orders[orderId]

    fun getOrders(): List<Order> = orders.values.toList()

    fun addProductToOrder(
        orderId: Int,
        productId: Int,
        quantity: Int = 1
    ) {
        val order = orders[orderId] ?: throw NoSuchElementException("заказа с id $orderId не существует")
        val product = catalog.get(productId) ?: throw NoSuchElementException("товара с id $productId нет в каталоге")

        order.addProduct(product, quantity)
    }

    fun findOrders(predicate: (Order) -> Boolean): List<Order> = orders.values.filter(predicate)

    fun getOrdersByStatus(statusType: OrderStatusType): List<Order> =
        findOrders { statusTypeOf(it.status) == statusType }

    fun calculatePaidRevenue(): Long =
        paidStatuses().fold(0L) { sum, status -> Math.addExact(sum, status.paidPrice) }

    fun getSoldProducts(): Map<Product, Int> {
        val sold = LinkedHashMap<Product, Int>()
        for (order in orders.values) {
            if (order.status !is OrderStatus.Paid) continue
            for (item in order.getItems()) {
                sold.merge(item.product, item.quantity, Math::addExact)
            }
        }
        return sold
    }

    private fun paidStatuses(): List<OrderStatus.Paid> =
        orders.values.mapNotNull { it.status as? OrderStatus.Paid }
}
