package orders

class OrderSystem(
    val catalog: ProductCatalog = ProductCatalog()
) {

    fun createOrder(
        orderId: Int,
        eventListener: ((OrderEvent) -> Unit)? = null
    ): Order {
        TODO()
    }

    fun getOrder(orderId: Int): Order? {
        TODO()
    }

    fun getOrders(): List<Order> {
        TODO()
    }

    fun addProductToOrder(
        orderId: Int,
        productId: Int,
        quantity: Int = 1
    ) {
        TODO()
    }

    fun findOrders(predicate: (Order) -> Boolean): List<Order> {
        TODO()
    }

    fun getOrdersByStatus(statusType: OrderStatusType): List<Order> {
        TODO()
    }

    fun calculatePaidRevenue(): Long {
        TODO()
    }

    fun getSoldProducts(): Map<Product, Int> {
        TODO()
    }
}
