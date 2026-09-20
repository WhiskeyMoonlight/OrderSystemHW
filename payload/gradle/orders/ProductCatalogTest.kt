package orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProductCatalogTest {

    @Test
    fun `new catalog is empty`() {
        assertEquals(emptyList(), ProductCatalog().getAll())
    }

    @Test
    fun `adds several products and keeps insertion order`() {
        val catalog = ProductCatalog()
        catalog.add(book())
        catalog.add(coffee())
        catalog.add(laptop())

        assertEquals(listOf(book(), coffee(), laptop()), catalog.getAll())
    }

    @Test
    fun `get returns stored product`() {
        val catalog = ProductCatalog()
        catalog.add(coffee())

        assertEquals(coffee(), catalog.get(1))
    }

    @Test
    fun `get returns null for unknown id`() {
        val catalog = ProductCatalog()
        catalog.add(coffee())

        assertNull(catalog.get(42))
    }

    @Test
    fun `get on empty catalog returns null`() {
        assertNull(ProductCatalog().get(1))
    }

    @Test
    fun `rejects duplicate id and keeps catalog unchanged`() {
        val catalog = ProductCatalog()
        catalog.add(coffee())

        assertFailsWith<IllegalArgumentException> {
            catalog.add(product(id = 1, name = "Another", priceInKopecks = 500))
        }
        assertEquals(listOf(coffee()), catalog.getAll())
        assertEquals(coffee(), catalog.get(1))
    }

    @Test
    fun `remove deletes product and returns it`() {
        val catalog = ProductCatalog()
        catalog.add(coffee())
        catalog.add(book())

        assertEquals(coffee(), catalog.remove(1))
        assertEquals(listOf(book()), catalog.getAll())
        assertNull(catalog.get(1))
    }

    @Test
    fun `remove of unknown product fails`() {
        val catalog = ProductCatalog()
        catalog.add(coffee())

        assertFailsWith<NoSuchElementException> { catalog.remove(99) }
        assertEquals(listOf(coffee()), catalog.getAll())
    }

    @Test
    fun `remove on empty catalog fails`() {
        assertFailsWith<NoSuchElementException> { ProductCatalog().remove(1) }
    }

    @Test
    fun `id becomes reusable after removal`() {
        val catalog = ProductCatalog()
        catalog.add(coffee())
        catalog.remove(1)
        catalog.add(product(id = 1, name = "Tea", priceInKopecks = 7_000))

        assertEquals("Tea", catalog.get(1)?.name)
    }

    @Test
    fun `re-added product moves to the end`() {
        val catalog = ProductCatalog()
        catalog.add(coffee())
        catalog.add(book())
        catalog.remove(1)
        catalog.add(coffee())

        assertEquals(listOf(book(), coffee()), catalog.getAll())
    }

    @Test
    fun `find returns only matching products`() {
        val catalog = ProductCatalog()
        catalog.add(coffee())
        catalog.add(book())
        catalog.add(laptop())

        assertEquals(listOf(book(), laptop()), catalog.find { it.priceInKopecks > 20_000 })
    }

    @Test
    fun `find returns empty list when nothing matches`() {
        val catalog = ProductCatalog()
        catalog.add(coffee())

        assertEquals(emptyList(), catalog.find { false })
    }

    @Test
    fun `find preserves insertion order`() {
        val catalog = ProductCatalog()
        catalog.add(laptop())
        catalog.add(coffee())
        catalog.add(book())

        assertEquals(listOf(laptop(), coffee(), book()), catalog.find { true })
    }

    @Test
    fun `getByCategory filters by category in insertion order`() {
        val catalog = ProductCatalog()
        catalog.add(coffee())
        catalog.add(book())
        catalog.add(product(id = 5, name = "Tea", priceInKopecks = 7_000, category = ProductCategory.FOOD))

        assertEquals(
            listOf(coffee(), product(id = 5, name = "Tea", priceInKopecks = 7_000, category = ProductCategory.FOOD)),
            catalog.getByCategory(ProductCategory.FOOD)
        )
    }

    @Test
    fun `getByCategory returns empty list for absent category`() {
        val catalog = ProductCatalog()
        catalog.add(coffee())

        assertEquals(emptyList(), catalog.getByCategory(ProductCategory.ELECTRONICS))
    }

    @Test
    fun `getAll does not expose internal storage`() {
        val catalog = ProductCatalog()
        catalog.add(coffee())

        val snapshot = catalog.getAll()
        runCatching { (snapshot as MutableList<*>).clear() }
        runCatching { (snapshot as MutableList<*>).removeAt(0) }

        assertEquals(listOf(coffee()), catalog.getAll())
    }

    @Test
    fun `find does not expose internal storage`() {
        val catalog = ProductCatalog()
        catalog.add(coffee())

        val snapshot = catalog.find { true }
        runCatching { (snapshot as MutableList<*>).clear() }

        assertEquals(listOf(coffee()), catalog.getAll())
    }

    @Test
    fun `getByCategory does not expose internal storage`() {
        val catalog = ProductCatalog()
        catalog.add(coffee())

        val snapshot = catalog.getByCategory(ProductCategory.FOOD)
        runCatching { (snapshot as MutableList<*>).clear() }

        assertEquals(listOf(coffee()), catalog.getByCategory(ProductCategory.FOOD))
    }

    @Test
    fun `catalog stores the very same product instance`() {
        val catalog = ProductCatalog()
        val original = coffee()
        catalog.add(original)

        assertSame(original, catalog.get(1))
    }

    @Test
    fun `predicate receives every product exactly once`() {
        val catalog = ProductCatalog()
        catalog.add(coffee())
        catalog.add(book())
        catalog.add(laptop())

        val seen = mutableListOf<Int>()
        catalog.find { seen += it.id; true }

        assertEquals(listOf(1, 2, 3), seen)
        assertTrue(seen.size == seen.distinct().size)
    }
}
