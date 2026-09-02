package orders

import kotlin.test.Test
import kotlin.test.assertEquals

class OrderProcessorTest {

    @Test
    fun `statusTypeOf maps created`() {
        assertEquals(OrderStatusType.CREATED, statusTypeOf(OrderStatus.Created))
    }

    @Test
    fun `statusTypeOf maps confirmed`() {
        val status = OrderStatus.Confirmed(PriceBreakdown(100, 0, 100), 2)

        assertEquals(OrderStatusType.CONFIRMED, statusTypeOf(status))
    }

    @Test
    fun `statusTypeOf maps paid`() {
        assertEquals(OrderStatusType.PAID, statusTypeOf(OrderStatus.Paid("tx", 100)))
    }

    @Test
    fun `statusTypeOf maps cancelled`() {
        val status = OrderStatus.Cancelled("stop", OrderStatusType.CONFIRMED)

        assertEquals(OrderStatusType.CANCELLED, statusTypeOf(status))
    }

    @Test
    fun `processOrder describes created order`() {
        val order = orderOf(id = 5, items = arrayOf(coffee() to 1))

        assertEquals("Order 5 is being edited", processOrder(order))
    }

    @Test
    fun `processOrder describes empty created order`() {
        assertEquals("Order 12 is being edited", processOrder(Order(12)))
    }

    @Test
    fun `processOrder describes confirmed order`() {
        val order = orderOf(id = 7, items = arrayOf(coffee() to 3, book() to 1))
        order.confirm(percentageDiscount(10))

        assertEquals("Order 7 is confirmed: 4 items, total 63900", processOrder(order))
    }

    @Test
    fun `processOrder describes paid order`() {
        val order = orderOf(id = 8, items = arrayOf(product(priceInKopecks = 10_000) to 1))
        order.confirm(fixedDiscount(1_000))
        order.pay("payment-2025-0001")

        assertEquals("Order 8 is paid: transaction payment-2025-0001, total 9000", processOrder(order))
    }

    @Test
    fun `processOrder describes order cancelled from created`() {
        val order = orderOf(id = 9, items = arrayOf(coffee() to 1))
        order.cancel("changed mind")

        assertEquals("Order 9 is cancelled from CREATED: changed mind", processOrder(order))
    }

    @Test
    fun `processOrder describes order cancelled from confirmed`() {
        val order = orderOf(id = 10, items = arrayOf(coffee() to 1))
        order.confirm()
        order.cancel("out of stock")

        assertEquals("Order 10 is cancelled from CONFIRMED: out of stock", processOrder(order))
    }

    @Test
    fun `processOrder follows the current status`() {
        val order = orderOf(id = 11, items = arrayOf(coffee() to 1))

        assertEquals("Order 11 is being edited", processOrder(order))

        order.confirm()
        assertEquals("Order 11 is confirmed: 1 items, total 12000", processOrder(order))

        order.reopen()
        assertEquals("Order 11 is being edited", processOrder(order))
    }
}
