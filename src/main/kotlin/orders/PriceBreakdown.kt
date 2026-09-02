package orders

data class PriceBreakdown(
    val subtotal: Long,
    val discount: Long,
    val total: Long
) {
    init {
        TODO()
    }
}
