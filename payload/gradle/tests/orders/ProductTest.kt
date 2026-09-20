package orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ProductTest {

    @Test
    fun `creates product with valid arguments`() {
        val product = Product(1, "Coffee", 12_000, ProductCategory.FOOD)

        assertEquals(1, product.id)
        assertEquals("Coffee", product.name)
        assertEquals(12_000, product.priceInKopecks)
        assertEquals(ProductCategory.FOOD, product.category)
    }

    @Test
    fun `accepts maximum price`() {
        assertEquals(Long.MAX_VALUE, product(priceInKopecks = Long.MAX_VALUE).priceInKopecks)
    }

    @Test
    fun `rejects zero id`() {
        assertFailsWith<IllegalArgumentException> { product(id = 0) }
    }

    @Test
    fun `rejects negative id`() {
        assertFailsWith<IllegalArgumentException> { product(id = -5) }
    }

    @Test
    fun `rejects empty name`() {
        assertFailsWith<IllegalArgumentException> { product(name = "") }
    }

    @Test
    fun `rejects blank name`() {
        assertFailsWith<IllegalArgumentException> { product(name = "   ") }
        assertFailsWith<IllegalArgumentException> { product(name = "\t\n ") }
    }

    @Test
    fun `rejects zero price`() {
        assertFailsWith<IllegalArgumentException> { product(priceInKopecks = 0) }
    }

    @Test
    fun `rejects negative price`() {
        assertFailsWith<IllegalArgumentException> { product(priceInKopecks = -1) }
    }

    @Test
    fun `keeps surrounding whitespace in name`() {
        assertEquals(" Coffee ", product(name = " Coffee ").name)
    }

    @Test
    fun `equal products have equal hash codes`() {
        val first = Product(1, "Coffee", 12_000, ProductCategory.FOOD)
        val second = Product(1, "Coffee", 12_000, ProductCategory.FOOD)

        assertEquals(first, second)
        assertEquals(first.hashCode(), second.hashCode())
    }

    @Test
    fun `products with different fields are not equal`() {
        val base = Product(1, "Coffee", 12_000, ProductCategory.FOOD)

        assertNotEquals(base, base.copy(id = 2))
        assertNotEquals(base, base.copy(name = "Tea"))
        assertNotEquals(base, base.copy(priceInKopecks = 12_001))
        assertNotEquals(base, base.copy(category = ProductCategory.OTHER))
    }
}
