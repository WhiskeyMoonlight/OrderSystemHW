package orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class DiscountTest {

    private fun items(vararg pairs: Pair<Product, Int>): List<OrderItem> =
        pairs.map { (product, quantity) -> OrderItem(product, quantity) }

    @Test
    fun `zero percent discount is zero`() {
        assertEquals(0, percentageDiscount(0)(items(coffee() to 5)))
    }

    @Test
    fun `hundred percent discount equals full price`() {
        assertEquals(60_000, percentageDiscount(100)(items(coffee() to 5)))
    }

    @Test
    fun `percentage discount takes share of the whole order`() {
        assertEquals(4_700, percentageDiscount(10)(items(coffee() to 1, book() to 1)))
    }

    @Test
    fun `percentage discount truncates fractional kopecks`() {
        assertEquals(149, percentageDiscount(15)(items(product(priceInKopecks = 999) to 1)))
    }

    @Test
    fun `percentage discount on empty order is zero`() {
        assertEquals(0, percentageDiscount(50)(emptyList()))
    }

    @Test
    fun `percentage discount rejects negative percent at creation`() {
        assertFailsWith<IllegalArgumentException> { percentageDiscount(-1) }
    }

    @Test
    fun `percentage discount rejects percent above hundred at creation`() {
        assertFailsWith<IllegalArgumentException> { percentageDiscount(101) }
    }

    @Test
    fun `fixed discount returns requested amount`() {
        assertEquals(700, fixedDiscount(700)(items(coffee() to 1)))
    }

    @Test
    fun `fixed discount is capped by order total`() {
        assertEquals(500, fixedDiscount(700)(items(product(priceInKopecks = 500) to 1)))
    }

    @Test
    fun `fixed discount of zero is allowed`() {
        assertEquals(0, fixedDiscount(0)(items(coffee() to 1)))
    }

    @Test
    fun `fixed discount on empty order is zero`() {
        assertEquals(0, fixedDiscount(700)(emptyList()))
    }

    @Test
    fun `fixed discount rejects negative amount at creation`() {
        assertFailsWith<IllegalArgumentException> { fixedDiscount(-1) }
    }

    @Test
    fun `category discount applies only to matching category`() {
        val discount = categoryDiscount(ProductCategory.BOOKS, 10)

        assertEquals(3_500, discount(items(coffee() to 2, book() to 1)))
    }

    @Test
    fun `category discount respects quantity`() {
        val discount = categoryDiscount(ProductCategory.FOOD, 50)

        assertEquals(18_000, discount(items(coffee() to 3, book() to 1)))
    }

    @Test
    fun `category discount returns zero for absent category`() {
        val discount = categoryDiscount(ProductCategory.ELECTRONICS, 30)

        assertEquals(0, discount(items(coffee() to 2, book() to 1)))
    }

    @Test
    fun `category discount truncates after summing matching items`() {
        val cheap = product(id = 7, priceInKopecks = 333, category = ProductCategory.HOME)
        val discount = categoryDiscount(ProductCategory.HOME, 15)

        assertEquals(149, discount(items(cheap to 3, coffee() to 1)))
    }

    @Test
    fun `category discount on empty order is zero`() {
        assertEquals(0, categoryDiscount(ProductCategory.FOOD, 20)(emptyList()))
    }

    @Test
    fun `category discount validates percent at creation`() {
        assertFailsWith<IllegalArgumentException> { categoryDiscount(ProductCategory.FOOD, -1) }
        assertFailsWith<IllegalArgumentException> { categoryDiscount(ProductCategory.FOOD, 101) }
    }

    @Test
    fun `combining no discounts yields zero`() {
        assertEquals(0, combineDiscounts()(items(coffee() to 5)))
    }

    @Test
    fun `combining a single discount keeps its result`() {
        assertEquals(6_000, combineDiscounts(percentageDiscount(10))(items(coffee() to 5)))
    }

    @Test
    fun `combining several discounts sums them`() {
        val order = items(product(priceInKopecks = 10_000) to 1)
        val discount = combineDiscounts(percentageDiscount(10), fixedDiscount(500))

        assertEquals(1_500, discount(order))
    }

    @Test
    fun `combined discount is capped by order total`() {
        val order = items(product(priceInKopecks = 500) to 1)
        val discount = combineDiscounts(percentageDiscount(50), fixedDiscount(400))

        assertEquals(500, discount(order))
    }

    @Test
    fun `combined discount fails on overflow`() {
        val order = items(coffee() to 1)
        val discount = combineDiscounts({ Long.MAX_VALUE }, { 1L })

        assertFailsWith<ArithmeticException> { discount(order) }
    }

    @Test
    fun `each combined discount receives the same snapshot`() {
        val order = items(coffee() to 2, book() to 1)
        val seen = mutableListOf<List<OrderItem>>()
        val discount = combineDiscounts(
            { snapshot -> seen += snapshot; 0L },
            { snapshot -> seen += snapshot; 0L }
        )

        discount(order)

        assertEquals(2, seen.size)
        assertEquals(order, seen[0])
        assertEquals(order, seen[1])
    }

    @Test
    fun `each combined discount is invoked exactly once`() {
        var first = 0
        var second = 0
        val discount = combineDiscounts({ first++; 0L }, { second++; 0L })

        discount(items(coffee() to 1))

        assertEquals(1, first)
        assertEquals(1, second)
    }

    @Test
    fun `discounts are reusable across orders`() {
        val discount = percentageDiscount(10)

        assertEquals(1_200, discount(items(coffee() to 1)))
        assertEquals(3_500, discount(items(book() to 1)))
    }
}
