package orders

data class OrderItem(
    val product: Product,
    val quantity: Int
) {
    init {
        TODO()
    }

    fun subtotal(): Long {
        TODO()
    }
}
