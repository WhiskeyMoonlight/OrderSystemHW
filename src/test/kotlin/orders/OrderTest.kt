package orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class OrderTest {

    @Test
    fun `new order is empty and created`() {
        val order = Order(1)

        assertEquals(1, order.id)
        assertEquals(OrderStatus.Created, order.status)
        assertEquals(emptyList(), order.getItems())
        assertEquals(emptyList(), order.getEvents())
    }

    @Test
    fun `rejects non positive id`() {
        assertFailsWith<IllegalArgumentException> { Order(0) }
        assertFailsWith<IllegalArgumentException> { Order(-1) }
    }

    @Test
    fun `works without event listener`() {
        val order = Order(1)
        order.addProduct(coffee())

        assertEquals(1, order.getEvents().size)
    }

    @Test
    fun `adds product with default quantity of one`() {
        val order = Order(1)
        order.addProduct(coffee())

        assertEquals(listOf(OrderItem(coffee(), 1)), order.getItems())
    }

    @Test
    fun `adds product with explicit quantity`() {
        val order = Order(1)
        order.addProduct(coffee(), 4)

        assertEquals(listOf(OrderItem(coffee(), 4)), order.getItems())
    }

    @Test
    fun `adds different products in insertion order`() {
        val order = orderOf(items = arrayOf(book() to 1, coffee() to 2, laptop() to 1))

        assertEquals(listOf(book(), coffee(), laptop()), order.getItems().map { it.product })
    }

    @Test
    fun `repeated add merges quantities into a single position`() {
        val order = Order(1)
        order.addProduct(coffee(), 2)
        order.addProduct(coffee(), 3)

        assertEquals(listOf(OrderItem(coffee(), 5)), order.getItems())
        assertEquals(2, order.getEvents().size)
    }

    @Test
    fun `repeated add keeps the original product instance`() {
        val order = Order(1)
        val original = coffee()
        order.addProduct(original, 1)
        order.addProduct(coffee(), 1)

        assertSame(original, order.getItems().single().product)
    }

    @Test
    fun `rejects product with same id but different data`() {
        val order = Order(1)
        order.addProduct(coffee(), 1)

        assertFailsWith<IllegalArgumentException> {
            order.addProduct(product(id = 1, name = "Tea", priceInKopecks = 12_000, category = ProductCategory.FOOD))
        }
        assertFailsWith<IllegalArgumentException> {
            order.addProduct(product(id = 1, name = "Coffee", priceInKopecks = 999, category = ProductCategory.FOOD))
        }
        assertFailsWith<IllegalArgumentException> {
            order.addProduct(product(id = 1, name = "Coffee", priceInKopecks = 12_000, category = ProductCategory.OTHER))
        }
        assertEquals(listOf(OrderItem(coffee(), 1)), order.getItems())
        assertEquals(1, order.getEvents().size)
    }

    @Test
    fun `rejects non positive quantity on add`() {
        val order = Order(1)

        assertFailsWith<IllegalArgumentException> { order.addProduct(coffee(), 0) }
        assertFailsWith<IllegalArgumentException> { order.addProduct(coffee(), -3) }
        assertEquals(emptyList(), order.getItems())
        assertEquals(emptyList(), order.getEvents())
    }

    @Test
    fun `add fails on quantity overflow and keeps order intact`() {
        val order = Order(1)
        order.addProduct(coffee(), Int.MAX_VALUE)

        assertFailsWith<ArithmeticException> { order.addProduct(coffee(), 1) }
        assertEquals(Int.MAX_VALUE, order.getItems().single().quantity)
        assertEquals(1, order.getEvents().size)
    }

    @Test
    fun `add emits ProductAdded with resulting quantity`() {
        val order = Order(1)
        order.addProduct(coffee(), 2)
        order.addProduct(coffee(), 3)

        assertEquals(
            listOf(
                OrderEvent.ProductAdded(1, 2, 2),
                OrderEvent.ProductAdded(1, 3, 5)
            ),
            order.getEvents()
        )
    }

    @Test
    fun `changes quantity of existing position`() {
        val order = orderOf(items = arrayOf(coffee() to 2))
        order.changeQuantity(1, 7)

        assertEquals(7, order.getItems().single().quantity)
        assertEquals(OrderEvent.QuantityChanged(1, 2, 7), order.getEvents().last())
    }

    @Test
    fun `changing quantity to the same value is a successful operation`() {
        val order = orderOf(items = arrayOf(coffee() to 2))
        order.changeQuantity(1, 2)

        assertEquals(2, order.getItems().single().quantity)
        assertEquals(OrderEvent.QuantityChanged(1, 2, 2), order.getEvents().last())
    }

    @Test
    fun `changing quantity keeps position order`() {
        val order = orderOf(items = arrayOf(coffee() to 1, book() to 1, laptop() to 1))
        order.changeQuantity(1, 9)

        assertEquals(listOf(1, 2, 3), order.getItems().map { it.product.id })
    }

    @Test
    fun `changing quantity of unknown position fails`() {
        val order = orderOf(items = arrayOf(coffee() to 1))

        assertFailsWith<NoSuchElementException> { order.changeQuantity(99, 3) }
        assertEquals(1, order.getEvents().size)
    }

    @Test
    fun `changing quantity to non positive value fails`() {
        val order = orderOf(items = arrayOf(coffee() to 2))

        assertFailsWith<IllegalArgumentException> { order.changeQuantity(1, 0) }
        assertFailsWith<IllegalArgumentException> { order.changeQuantity(1, -1) }
        assertEquals(2, order.getItems().single().quantity)
        assertEquals(1, order.getEvents().size)
    }

    @Test
    fun `removes existing position and returns it`() {
        val order = orderOf(items = arrayOf(coffee() to 2, book() to 1))

        assertEquals(OrderItem(coffee(), 2), order.removeProduct(1))
        assertEquals(listOf(OrderItem(book(), 1)), order.getItems())
        assertEquals(OrderEvent.ProductRemoved(1, 2), order.getEvents().last())
    }

    @Test
    fun `removing unknown position fails`() {
        val order = orderOf(items = arrayOf(coffee() to 1))

        assertFailsWith<NoSuchElementException> { order.removeProduct(99) }
        assertEquals(1, order.getItems().size)
        assertEquals(1, order.getEvents().size)
    }

    @Test
    fun `re-added position goes to the end`() {
        val order = orderOf(items = arrayOf(coffee() to 1, book() to 1, laptop() to 1))
        order.removeProduct(1)
        order.addProduct(coffee(), 1)

        assertEquals(listOf(2, 3, 1), order.getItems().map { it.product.id })
    }

    @Test
    fun `clear removes every position and emits one event`() {
        val order = orderOf(items = arrayOf(coffee() to 2, book() to 1))
        order.clear()

        assertEquals(emptyList(), order.getItems())
        assertEquals(OrderEvent.OrderCleared, order.getEvents().last())
        assertEquals(3, order.getEvents().size)
    }

    @Test
    fun `clear on empty order still emits an event`() {
        val order = Order(1)
        order.clear()

        assertEquals(listOf(OrderEvent.OrderCleared), order.getEvents())
    }

    @Test
    fun `findItem returns position or null`() {
        val order = orderOf(items = arrayOf(coffee() to 2))

        assertEquals(OrderItem(coffee(), 2), order.findItem(1))
        assertNull(order.findItem(99))
    }

    @Test
    fun `subtotal of empty order is zero`() {
        assertEquals(0, Order(1).calculateSubtotal())
    }

    @Test
    fun `subtotal sums every position`() {
        val order = orderOf(items = arrayOf(coffee() to 3, book() to 1))

        assertEquals(71_000, order.calculateSubtotal())
    }

    @Test
    fun `subtotal fails on overflow`() {
        val order = Order(1)
        order.addProduct(product(id = 1, priceInKopecks = Long.MAX_VALUE), 1)
        order.addProduct(product(id = 2, priceInKopecks = 1), 1)

        assertFailsWith<ArithmeticException> { order.calculateSubtotal() }
    }

    @Test
    fun `getItems does not expose internal storage`() {
        val order = orderOf(items = arrayOf(coffee() to 1))

        val snapshot = order.getItems()
        runCatching { (snapshot as MutableList<*>).clear() }

        assertEquals(1, order.getItems().size)
    }

    @Test
    fun `getEvents does not expose internal storage`() {
        val order = orderOf(items = arrayOf(coffee() to 1))

        val snapshot = order.getEvents()
        runCatching { (snapshot as MutableList<*>).clear() }

        assertEquals(1, order.getEvents().size)
    }

    @Test
    fun `listener receives every event exactly once`() {
        val received = mutableListOf<OrderEvent>()
        val order = Order(1) { received += it }

        order.addProduct(coffee(), 2)
        order.changeQuantity(1, 3)
        order.removeProduct(1)
        order.clear()

        assertEquals(order.getEvents(), received)
        assertEquals(4, received.size)
    }

    @Test
    fun `listener is not notified for failed operations`() {
        val received = mutableListOf<OrderEvent>()
        val order = Order(1) { received += it }

        runCatching { order.addProduct(coffee(), 0) }
        runCatching { order.changeQuantity(1, 5) }
        runCatching { order.removeProduct(1) }

        assertEquals(emptyList(), received.toList())
    }

    @Test
    fun `confirm moves order to confirmed with price snapshot`() {
        val order = orderOf(items = arrayOf(coffee() to 3, book() to 1))
        order.confirm()

        val status = order.status
        assertIs<OrderStatus.Confirmed>(status)
        assertEquals(PriceBreakdown(71_000, 0, 71_000), status.price)
        assertEquals(4, status.itemCount)
        assertEquals(OrderEvent.OrderConfirmed(PriceBreakdown(71_000, 0, 71_000)), order.getEvents().last())
    }

    @Test
    fun `confirm applies discount`() {
        val order = orderOf(items = arrayOf(product(priceInKopecks = 10_000) to 1))
        order.confirm(percentageDiscount(15))

        val status = order.status
        assertIs<OrderStatus.Confirmed>(status)
        assertEquals(PriceBreakdown(10_000, 1_500, 8_500), status.price)
    }

    @Test
    fun `confirm counts units and not positions`() {
        val order = orderOf(items = arrayOf(coffee() to 10, book() to 5))
        order.confirm()

        assertEquals(15, (order.status as OrderStatus.Confirmed).itemCount)
    }

    @Test
    fun `confirm passes message to logger exactly once`() {
        val order = orderOf(items = arrayOf(product(priceInKopecks = 10_000) to 1))
        val messages = mutableListOf<String>()

        order.confirm(percentageDiscount(15)) { messages += it }

        assertEquals(listOf("Order 1: subtotal=10000, discount=1500, total=8500"), messages)
    }

    @Test
    fun `confirm works with null discount and null logger`() {
        val order = orderOf(items = arrayOf(coffee() to 1))
        order.confirm(null, null)

        assertIs<OrderStatus.Confirmed>(order.status)
    }

    @Test
    fun `confirm of empty order fails`() {
        val order = Order(1)

        assertFailsWith<IllegalStateException> { order.confirm() }
        assertEquals(OrderStatus.Created, order.status)
        assertEquals(emptyList(), order.getEvents())
    }

    @Test
    fun `confirm freezes the discount result`() {
        var percent = 10
        val order = orderOf(items = arrayOf(product(priceInKopecks = 10_000) to 1))
        order.confirm({ snapshot -> snapshot.sumOf { it.subtotal() } * percent / 100 })

        val before = (order.status as OrderStatus.Confirmed).price
        percent = 90

        assertEquals(before, (order.status as OrderStatus.Confirmed).price)
        assertEquals(PriceBreakdown(10_000, 1_000, 9_000), before)
    }

    @Test
    fun `confirm rejects invalid discount lambda`() {
        val order = orderOf(items = arrayOf(product(priceInKopecks = 10_000) to 1))

        assertFailsWith<IllegalArgumentException> { order.confirm({ -1L }) }
        assertFailsWith<IllegalArgumentException> { order.confirm({ 10_001L }) }
        assertEquals(OrderStatus.Created, order.status)
        assertEquals(1, order.getEvents().size)
    }

    @Test
    fun `confirm fails on item count overflow`() {
        val order = Order(1)
        order.addProduct(product(id = 1, priceInKopecks = 1), Int.MAX_VALUE)
        order.addProduct(product(id = 2, priceInKopecks = 1), Int.MAX_VALUE)

        assertFailsWith<ArithmeticException> { order.confirm() }
        assertEquals(OrderStatus.Created, order.status)
        assertEquals(2, order.getEvents().size)
    }

    @Test
    fun `confirm is forbidden from other states`() {
        val order = orderOf(items = arrayOf(coffee() to 1))
        order.confirm()

        assertFailsWith<IllegalStateException> { order.confirm() }
    }

    @Test
    fun `editing a confirmed order is forbidden`() {
        val order = orderOf(items = arrayOf(coffee() to 1))
        order.confirm()

        assertFailsWith<IllegalStateException> { order.addProduct(book(), 1) }
        assertFailsWith<IllegalStateException> { order.changeQuantity(1, 5) }
        assertFailsWith<IllegalStateException> { order.removeProduct(1) }
        assertFailsWith<IllegalStateException> { order.clear() }
        assertEquals(listOf(OrderItem(coffee(), 1)), order.getItems())
    }

    @Test
    fun `editing a paid order is forbidden`() {
        val order = orderOf(items = arrayOf(coffee() to 1))
        order.confirm()
        order.pay("tx-1")

        assertFailsWith<IllegalStateException> { order.addProduct(book(), 1) }
        assertFailsWith<IllegalStateException> { order.changeQuantity(1, 5) }
        assertFailsWith<IllegalStateException> { order.removeProduct(1) }
        assertFailsWith<IllegalStateException> { order.clear() }
    }

    @Test
    fun `editing a cancelled order is forbidden`() {
        val order = orderOf(items = arrayOf(coffee() to 1))
        order.cancel("no reason")

        assertFailsWith<IllegalStateException> { order.addProduct(book(), 1) }
        assertFailsWith<IllegalStateException> { order.changeQuantity(1, 5) }
        assertFailsWith<IllegalStateException> { order.removeProduct(1) }
        assertFailsWith<IllegalStateException> { order.clear() }
    }

    @Test
    fun `reopen returns order to editing and keeps items`() {
        val order = orderOf(items = arrayOf(coffee() to 2))
        order.confirm(percentageDiscount(50))
        order.reopen()

        assertEquals(OrderStatus.Created, order.status)
        assertEquals(listOf(OrderItem(coffee(), 2)), order.getItems())
        assertEquals(OrderEvent.OrderReopened, order.getEvents().last())
    }

    @Test
    fun `reopen drops the previously applied discount`() {
        val order = orderOf(items = arrayOf(coffee() to 2))
        order.confirm(percentageDiscount(50))
        order.reopen()
        order.confirm()

        assertEquals(PriceBreakdown(24_000, 0, 24_000), (order.status as OrderStatus.Confirmed).price)
    }

    @Test
    fun `reopen allows editing again`() {
        val order = orderOf(items = arrayOf(coffee() to 2))
        order.confirm()
        order.reopen()
        order.addProduct(book(), 1)

        assertEquals(2, order.getItems().size)
    }

    @Test
    fun `reopen is forbidden outside confirmed`() {
        assertFailsWith<IllegalStateException> { Order(1).reopen() }

        val paid = orderOf(items = arrayOf(coffee() to 1))
        paid.confirm()
        paid.pay("tx-1")
        assertFailsWith<IllegalStateException> { paid.reopen() }

        val cancelled = orderOf(items = arrayOf(coffee() to 1))
        cancelled.cancel("stop")
        assertFailsWith<IllegalStateException> { cancelled.reopen() }
    }

    @Test
    fun `pay moves confirmed order to paid`() {
        val order = orderOf(items = arrayOf(product(priceInKopecks = 10_000) to 1))
        order.confirm(fixedDiscount(2_000))
        order.pay("payment-1")

        assertEquals(OrderStatus.Paid("payment-1", 8_000), order.status)
        assertEquals(OrderEvent.OrderPaid("payment-1", 8_000), order.getEvents().last())
    }

    @Test
    fun `pay keeps transaction id untouched`() {
        val order = orderOf(items = arrayOf(coffee() to 1))
        order.confirm()
        order.pay("  tx  ")

        assertEquals("  tx  ", (order.status as OrderStatus.Paid).transactionId)
    }

    @Test
    fun `pay rejects blank transaction id`() {
        val order = orderOf(items = arrayOf(coffee() to 1))
        order.confirm()

        assertFailsWith<IllegalArgumentException> { order.pay("") }
        assertFailsWith<IllegalArgumentException> { order.pay("   ") }
        assertIs<OrderStatus.Confirmed>(order.status)
    }

    @Test
    fun `pay is forbidden outside confirmed`() {
        assertFailsWith<IllegalStateException> { Order(1).pay("tx-1") }

        val order = orderOf(items = arrayOf(coffee() to 1))
        order.confirm()
        order.pay("tx-1")
        assertFailsWith<IllegalStateException> { order.pay("tx-2") }

        val cancelled = orderOf(items = arrayOf(coffee() to 1))
        cancelled.cancel("stop")
        assertFailsWith<IllegalStateException> { cancelled.pay("tx-1") }
    }

    @Test
    fun `cancel from created stores previous status`() {
        val order = orderOf(items = arrayOf(coffee() to 1))
        order.cancel("changed mind")

        assertEquals(OrderStatus.Cancelled("changed mind", OrderStatusType.CREATED), order.status)
        assertEquals(OrderEvent.OrderCancelled("changed mind", OrderStatusType.CREATED), order.getEvents().last())
    }

    @Test
    fun `cancel from confirmed stores previous status`() {
        val order = orderOf(items = arrayOf(coffee() to 1))
        order.confirm()
        order.cancel("out of stock")

        assertEquals(OrderStatus.Cancelled("out of stock", OrderStatusType.CONFIRMED), order.status)
    }

    @Test
    fun `cancel of empty created order is allowed`() {
        val order = Order(1)
        order.cancel("never used")

        assertIs<OrderStatus.Cancelled>(order.status)
    }

    @Test
    fun `cancel keeps reason untouched`() {
        val order = Order(1)
        order.cancel("  reason  ")

        assertEquals("  reason  ", (order.status as OrderStatus.Cancelled).reason)
    }

    @Test
    fun `cancel rejects blank reason`() {
        val order = Order(1)

        assertFailsWith<IllegalArgumentException> { order.cancel("") }
        assertFailsWith<IllegalArgumentException> { order.cancel("  \t ") }
        assertEquals(OrderStatus.Created, order.status)
        assertEquals(emptyList(), order.getEvents())
    }

    @Test
    fun `cancel is forbidden for paid and cancelled orders`() {
        val paid = orderOf(items = arrayOf(coffee() to 1))
        paid.confirm()
        paid.pay("tx-1")
        assertFailsWith<IllegalStateException> { paid.cancel("late") }

        val cancelled = Order(2)
        cancelled.cancel("first")
        assertFailsWith<IllegalStateException> { cancelled.cancel("second") }
        assertEquals(OrderStatus.Cancelled("first", OrderStatusType.CREATED), cancelled.status)
    }

    @Test
    fun `full lifecycle produces the expected event log`() {
        val order = Order(7)
        order.addProduct(coffee(), 2)
        order.changeQuantity(1, 3)
        order.confirm()
        order.reopen()
        order.addProduct(book(), 1)
        order.confirm()
        order.pay("tx-9")

        assertEquals(
            listOf(
                OrderEvent.ProductAdded(1, 2, 2),
                OrderEvent.QuantityChanged(1, 2, 3),
                OrderEvent.OrderConfirmed(PriceBreakdown(36_000, 0, 36_000)),
                OrderEvent.OrderReopened,
                OrderEvent.ProductAdded(2, 1, 1),
                OrderEvent.OrderConfirmed(PriceBreakdown(71_000, 0, 71_000)),
                OrderEvent.OrderPaid("tx-9", 71_000)
            ),
            order.getEvents()
        )
    }

    @Test
    fun `order is a price calculator`() {
        val calculator: PriceCalculator = orderOf(items = arrayOf(coffee() to 2))

        assertEquals(24_000, calculator.calculateSubtotal())
    }

    @Test
    fun `independent orders do not share state`() {
        val first = orderOf(id = 1, items = arrayOf(coffee() to 1))
        val second = Order(2)

        assertTrue(first.getItems().isNotEmpty())
        assertTrue(second.getItems().isEmpty())
        assertFalse(second.getEvents().isNotEmpty())
    }
}
