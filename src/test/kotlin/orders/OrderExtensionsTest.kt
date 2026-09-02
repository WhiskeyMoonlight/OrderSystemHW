package orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OrderExtensionsTest {

    @Test
    fun `calculatePrice without discount returns full subtotal`() {
        val order = orderOf(items = arrayOf(coffee() to 3, book() to 1))

        assertEquals(PriceBreakdown(71_000, 0, 71_000), order.calculatePrice())
    }

    @Test
    fun `calculatePrice of empty order is zero`() {
        assertEquals(PriceBreakdown(0, 0, 0), Order(1).calculatePrice())
    }

    @Test
    fun `calculatePrice applies discount`() {
        val order = orderOf(items = arrayOf(product(priceInKopecks = 10_000) to 1))

        assertEquals(PriceBreakdown(10_000, 1_500, 8_500), order.calculatePrice(percentageDiscount(15)))
    }

    @Test
    fun `calculatePrice supports full discount`() {
        val order = orderOf(items = arrayOf(product(priceInKopecks = 10_000) to 1))

        assertEquals(PriceBreakdown(10_000, 10_000, 0), order.calculatePrice(percentageDiscount(100)))
    }

    @Test
    fun `calculatePrice invokes discount exactly once`() {
        val order = orderOf(items = arrayOf(coffee() to 2))
        var calls = 0

        order.calculatePrice({ calls++; 0L })

        assertEquals(1, calls)
    }

    @Test
    fun `calculatePrice passes current items snapshot to discount`() {
        val order = orderOf(items = arrayOf(coffee() to 2, book() to 1))
        var snapshot: List<OrderItem> = emptyList()

        order.calculatePrice({ snapshot = it; 0L })

        assertEquals(order.getItems(), snapshot)
    }

    @Test
    fun `calculatePrice rejects negative discount result`() {
        val order = orderOf(items = arrayOf(coffee() to 1))

        assertFailsWith<IllegalArgumentException> { order.calculatePrice({ -1L }) }
    }

    @Test
    fun `calculatePrice rejects discount above subtotal`() {
        val order = orderOf(items = arrayOf(coffee() to 1))

        assertFailsWith<IllegalArgumentException> { order.calculatePrice({ 12_001L }) }
    }

    @Test
    fun `calculatePrice allows discount equal to subtotal`() {
        val order = orderOf(items = arrayOf(coffee() to 1))

        assertEquals(PriceBreakdown(12_000, 12_000, 0), order.calculatePrice({ 12_000L }))
    }

    @Test
    fun `calculatePrice calls logger once with the exact message`() {
        val order = orderOf(id = 7, items = arrayOf(product(priceInKopecks = 10_000) to 1))
        val messages = mutableListOf<String>()

        order.calculatePrice(percentageDiscount(15)) { messages += it }

        assertEquals(listOf("Order 7: subtotal=10000, discount=1500, total=8500"), messages)
    }

    @Test
    fun `calculatePrice logs zero discount as well`() {
        val order = orderOf(id = 3, items = arrayOf(coffee() to 1))
        val messages = mutableListOf<String>()

        order.calculatePrice(null) { messages += it }

        assertEquals(listOf("Order 3: subtotal=12000, discount=0, total=12000"), messages)
    }

    @Test
    fun `calculatePrice does not call logger on failure`() {
        val order = orderOf(items = arrayOf(coffee() to 1))
        var calls = 0

        assertFailsWith<IllegalArgumentException> {
            order.calculatePrice({ -5L }) { calls++ }
        }

        assertEquals(0, calls)
    }

    @Test
    fun `calculatePrice works without logger`() {
        val order = orderOf(items = arrayOf(coffee() to 1))

        assertEquals(PriceBreakdown(12_000, 0, 12_000), order.calculatePrice(null, null))
    }

    @Test
    fun `calculatePrice does not change the order`() {
        val order = orderOf(items = arrayOf(coffee() to 2))
        val itemsBefore = order.getItems()
        val eventsBefore = order.getEvents()

        order.calculatePrice(percentageDiscount(20))

        assertEquals(itemsBefore, order.getItems())
        assertEquals(eventsBefore, order.getEvents())
        assertEquals(OrderStatus.Created, order.status)
    }

    @Test
    fun `calculatePrice fails on subtotal overflow`() {
        val order = Order(1)
        order.addProduct(product(id = 1, priceInKopecks = Long.MAX_VALUE), 1)
        order.addProduct(product(id = 2, priceInKopecks = 1), 1)

        assertFailsWith<ArithmeticException> { order.calculatePrice() }
    }

    @Test
    fun `containsProduct detects present and absent products`() {
        val order = orderOf(items = arrayOf(coffee() to 1, book() to 2))

        assertTrue(order.containsProduct(1))
        assertTrue(order.containsProduct(2))
        assertFalse(order.containsProduct(3))
    }

    @Test
    fun `containsProduct on empty order is false`() {
        assertFalse(Order(1).containsProduct(1))
    }

    @Test
    fun `containsProduct reflects removal`() {
        val order = orderOf(items = arrayOf(coffee() to 1))
        order.removeProduct(1)

        assertFalse(order.containsProduct(1))
    }

    @Test
    fun `toSummary renders created order`() {
        val order = orderOf(id = 7, items = arrayOf(coffee() to 3, book() to 2))

        assertEquals("Order 7: status=CREATED, positions=2, items=5, subtotal=106000", order.toSummary())
    }

    @Test
    fun `toSummary renders empty order`() {
        assertEquals("Order 1: status=CREATED, positions=0, items=0, subtotal=0", Order(1).toSummary())
    }

    @Test
    fun `toSummary renders confirmed order`() {
        val order = orderOf(id = 2, items = arrayOf(coffee() to 1))
        order.confirm(percentageDiscount(50))

        assertEquals("Order 2: status=CONFIRMED, positions=1, items=1, subtotal=12000", order.toSummary())
    }

    @Test
    fun `toSummary renders paid order`() {
        val order = orderOf(id = 3, items = arrayOf(coffee() to 2))
        order.confirm()
        order.pay("tx-1")

        assertEquals("Order 3: status=PAID, positions=1, items=2, subtotal=24000", order.toSummary())
    }

    @Test
    fun `toSummary renders cancelled order`() {
        val order = orderOf(id = 4, items = arrayOf(book() to 1))
        order.cancel("no longer needed")

        assertEquals("Order 4: status=CANCELLED, positions=1, items=1, subtotal=35000", order.toSummary())
    }

    @Test
    fun `toSummary fails on item count overflow`() {
        val order = Order(1)
        order.addProduct(product(id = 1, priceInKopecks = 1), Int.MAX_VALUE)
        order.addProduct(product(id = 2, priceInKopecks = 1), Int.MAX_VALUE)

        assertFailsWith<ArithmeticException> { order.toSummary() }
    }
}
