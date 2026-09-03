package orders

fun Order.calculatePrice(
    discount: Discount? = null,
    logger: ((String) -> Unit)? = null
): PriceBreakdown {
    val subtotal = calculateSubtotal()
    val discountValue = discount?.invoke(getItems()) ?: 0

    require(discountValue >= 0) { "скидка не может быть отрицательной, получено $discountValue" }
    require(discountValue <= subtotal) { "скидка $discountValue больше суммы заказа $subtotal" }

    val total = subtotal - discountValue
    logger?.invoke("Order $id: subtotal=$subtotal, discount=$discountValue, total=$total")
    return PriceBreakdown(subtotal, discountValue, total)
}

fun Order.containsProduct(productId: Int): Boolean = findItem(productId) != null

fun Order.toSummary(): String {
    val items = getItems()
    return "Order $id: status=${statusTypeOf(status)}, " +
        "positions=${items.size}, items=${items.countUnits()}, subtotal=${calculateSubtotal()}"
}

// Суммарное число единиц товара, а не позиций. addExact, потому что
// количество в позиции может быть близко к Int.MAX_VALUE.
internal fun Collection<OrderItem>.countUnits(): Int =
    fold(0) { sum, item -> Math.addExact(sum, item.quantity) }
