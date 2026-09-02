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

    init {
        TODO()
    }

    fun getItems(): List<OrderItem> {
        TODO()
    }

    fun getEvents(): List<OrderEvent> {
        TODO()
    }

    fun addProduct(product: Product, quantity: Int = 1) {
        TODO()
    }

    fun changeQuantity(productId: Int, newQuantity: Int) {
        TODO()
    }

    fun removeProduct(productId: Int): OrderItem {
        TODO()
    }

    fun clear() {
        TODO()
    }

    fun findItem(productId: Int): OrderItem? {
        TODO()
    }

    override fun calculateSubtotal(): Long {
        TODO()
    }

    fun confirm(
        discount: Discount? = null,
        discountLogger: ((String) -> Unit)? = null
    ) {
        TODO()
    }

    fun reopen() {
        TODO()
    }

    fun pay(transactionId: String) {
        TODO()
    }

    fun cancel(reason: String) {
        TODO()
    }
}
