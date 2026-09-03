package orders

data class PriceBreakdown(
    val subtotal: Long,
    val discount: Long,
    val total: Long
) {
    init {
        require(subtotal >= 0) { "subtotal не может быть отрицательным" }
        require(discount >= 0) { "скидка не может быть отрицательной" }
        require(discount <= subtotal) { "скидка $discount больше суммы заказа $subtotal" }
        require(total == subtotal - discount) { "total должен быть равен subtotal - discount" }
    }
}
