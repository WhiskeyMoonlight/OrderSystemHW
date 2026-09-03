package orders

typealias Discount = (List<OrderItem>) -> Long

private fun requirePercent(percent: Int) =
    require(percent in 0..100) { "процент скидки должен быть в диапазоне 0..100, получено $percent" }

private fun List<OrderItem>.total(): Long =
    fold(0L) { sum, item -> Math.addExact(sum, item.subtotal()) }

// Копейки округляются вниз: 999 * 15 / 100 = 149, а не 150.
private fun share(amount: Long, percent: Int): Long =
    Math.multiplyExact(amount, percent.toLong()) / 100

fun percentageDiscount(percent: Int): Discount {
    requirePercent(percent)
    return { items -> share(items.total(), percent) }
}

fun fixedDiscount(amountInKopecks: Long): Discount {
    require(amountInKopecks >= 0) { "скидка не может быть отрицательной, получено $amountInKopecks" }
    return { items -> minOf(amountInKopecks, items.total()) }
}

fun categoryDiscount(
    category: ProductCategory,
    percent: Int
): Discount {
    requirePercent(percent)
    return { items -> share(items.filter { it.product.category == category }.total(), percent) }
}

// Скидки складываются и лишь потом обрезаются по сумме заказа:
// иначе порядок аргументов влиял бы на результат.
fun combineDiscounts(vararg discounts: Discount): Discount = { items ->
    val sum = discounts.fold(0L) { acc, discount -> Math.addExact(acc, discount(items)) }
    minOf(sum, items.total())
}
