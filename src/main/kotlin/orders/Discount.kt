package orders

typealias Discount = (List<OrderItem>) -> Long

fun percentageDiscount(percent: Int): Discount {
    TODO()
}

fun fixedDiscount(amountInKopecks: Long): Discount {
    TODO()
}

fun categoryDiscount(
    category: ProductCategory,
    percent: Int
): Discount {
    TODO()
}

fun combineDiscounts(vararg discounts: Discount): Discount {
    TODO()
}
