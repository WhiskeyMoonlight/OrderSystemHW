package orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OrderItemTest {

    @Test
    fun `creates item with positive quantity`() {
        val item = OrderItem(coffee(), 3)

        assertEquals(coffee(), item.product)
        assertEquals(3, item.quantity)
    }

    @Test
    fun `rejects zero quantity`() {
        assertFailsWith<IllegalArgumentException> { OrderItem(coffee(), 0) }
    }

    @Test
    fun `rejects negative quantity`() {
        assertFailsWith<IllegalArgumentException> { OrderItem(coffee(), -2) }
    }

    @Test
    fun `subtotal multiplies price by quantity`() {
        assertEquals(36_000, OrderItem(coffee(), 3).subtotal())
    }

    @Test
    fun `subtotal of single unit equals price`() {
        assertEquals(12_000, OrderItem(coffee(), 1).subtotal())
    }

    @Test
    fun `subtotal handles large but valid values`() {
        val item = OrderItem(product(priceInKopecks = 4_000_000_000L), 2)

        assertEquals(8_000_000_000L, item.subtotal())
    }

    @Test
    fun `subtotal fails on overflow`() {
        val item = OrderItem(product(priceInKopecks = Long.MAX_VALUE), 2)

        assertFailsWith<ArithmeticException> { item.subtotal() }
    }

    @Test
    fun `subtotal fails on overflow with large quantity`() {
        val item = OrderItem(product(priceInKopecks = Long.MAX_VALUE / 2), 5)

        assertFailsWith<ArithmeticException> { item.subtotal() }
    }

    @Test
    fun `items are compared structurally`() {
        assertEquals(OrderItem(coffee(), 2), OrderItem(coffee(), 2))
        assertEquals(OrderItem(coffee(), 2).hashCode(), OrderItem(coffee(), 2).hashCode())
    }

    @Test
    fun `copy validates quantity`() {
        assertFailsWith<IllegalArgumentException> { OrderItem(coffee(), 2).copy(quantity = 0) }
    }
}
