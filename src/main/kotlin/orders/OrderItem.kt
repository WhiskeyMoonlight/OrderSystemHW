package orders

data class OrderItem(
    val product: Product,
    val quantity: Int
) {
    init {
        require(quantity > 0) { "количество должно быть положительным, получено $quantity" }
    }

    fun subtotal(): Long = Math.multiplyExact(product.priceInKopecks, quantity.toLong())
}
