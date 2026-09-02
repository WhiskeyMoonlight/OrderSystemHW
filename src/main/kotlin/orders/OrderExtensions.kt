package orders

fun Order.calculatePrice(
    discount: Discount? = null,
    logger: ((String) -> Unit)? = null
): PriceBreakdown {
    TODO()
}

fun Order.containsProduct(productId: Int): Boolean {
    TODO()
}

fun Order.toSummary(): String {
    TODO()
}
