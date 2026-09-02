package orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MiniOrderSystemIntegrationTest {

    @Test
    fun `reference scenario from the assignment`() {
        val events = mutableListOf<OrderEvent>()
        val logs = mutableListOf<String>()
        val system = OrderSystem()

        val coffee = Product(1, "Coffee", 12_000, ProductCategory.FOOD)
        val book = Product(2, "Kotlin in Action", 35_000, ProductCategory.BOOKS)

        system.catalog.add(coffee)
        system.catalog.add(book)

        val order = system.createOrder(orderId = 101) { events += it }

        system.addProductToOrder(orderId = 101, productId = 1, quantity = 2)
        system.addProductToOrder(orderId = 101, productId = 2)
        order.changeQuantity(productId = 1, newQuantity = 3)

        assertEquals(71_000, order.calculateSubtotal())
        assertEquals("Order 101 is being edited", processOrder(order))

        val discount = combineDiscounts(
            percentageDiscount(5),
            categoryDiscount(ProductCategory.BOOKS, 10)
        )

        order.confirm(discount) { logs += it }

        assertEquals(listOf("Order 101: subtotal=71000, discount=7050, total=63950"), logs)
        assertEquals(
            OrderStatus.Confirmed(PriceBreakdown(71_000, 7_050, 63_950), 4),
            order.status
        )
        assertEquals("Order 101 is confirmed: 4 items, total 63950", processOrder(order))

        order.pay(transactionId = "payment-2025-0001")

        assertEquals(OrderStatus.Paid("payment-2025-0001", 63_950), order.status)
        assertEquals(
            "Order 101 is paid: transaction payment-2025-0001, total 63950",
            processOrder(order)
        )
        assertEquals("Order 101: status=PAID, positions=2, items=4, subtotal=71000", order.toSummary())
        assertEquals(63_950, system.calculatePaidRevenue())
        assertEquals(mapOf(coffee to 3, book to 1), system.getSoldProducts())
        assertEquals(
            listOf(
                OrderEvent.ProductAdded(1, 2, 2),
                OrderEvent.ProductAdded(2, 1, 1),
                OrderEvent.QuantityChanged(1, 2, 3),
                OrderEvent.OrderConfirmed(PriceBreakdown(71_000, 7_050, 63_950)),
                OrderEvent.OrderPaid("payment-2025-0001", 63_950)
            ),
            events
        )
    }

    @Test
    fun `shop lifecycle across several orders`() {
        val system = OrderSystem()
        system.catalog.add(coffee())
        system.catalog.add(book())
        system.catalog.add(laptop())
        system.catalog.add(lamp())

        val paidBooks = system.createOrder(1)
        val paidMixed = system.createOrder(2)
        val confirmed = system.createOrder(3)
        val cancelled = system.createOrder(4)
        val editing = system.createOrder(5)

        system.addProductToOrder(1, 2, 2)
        system.addProductToOrder(2, 1, 4)
        system.addProductToOrder(2, 2, 1)
        system.addProductToOrder(2, 4, 3)
        system.addProductToOrder(3, 3, 1)
        system.addProductToOrder(4, 1, 1)
        system.addProductToOrder(5, 4, 10)

        paidBooks.confirm(categoryDiscount(ProductCategory.BOOKS, 10))
        paidBooks.pay("tx-books")

        paidMixed.confirm(combineDiscounts(percentageDiscount(5), fixedDiscount(1_000)))
        paidMixed.pay("tx-mixed")

        confirmed.confirm()
        cancelled.cancel("customer request")

        assertEquals(listOf(editing), system.getOrdersByStatus(OrderStatusType.CREATED))
        assertEquals(listOf(confirmed), system.getOrdersByStatus(OrderStatusType.CONFIRMED))
        assertEquals(listOf(paidBooks, paidMixed), system.getOrdersByStatus(OrderStatusType.PAID))
        assertEquals(listOf(cancelled), system.getOrdersByStatus(OrderStatusType.CANCELLED))

        assertEquals(63_000, (paidBooks.status as OrderStatus.Paid).paidPrice)
        assertEquals(90_958, (paidMixed.status as OrderStatus.Paid).paidPrice)
        assertEquals(153_958, system.calculatePaidRevenue())

        assertEquals(
            mapOf(book() to 3, coffee() to 4, lamp() to 3),
            system.getSoldProducts()
        )
        assertEquals(listOf(book(), coffee(), lamp()), system.getSoldProducts().keys.toList())

        assertEquals(
            listOf(1, 2, 3),
            system.findOrders { it.calculateSubtotal() > 50_000 }.map { it.id }
        )
    }

    @Test
    fun `order returns to editing and is repriced`() {
        val system = OrderSystem()
        system.catalog.add(coffee())
        system.catalog.add(book())

        val order = system.createOrder(1)
        system.addProductToOrder(1, 1, 2)
        order.confirm(percentageDiscount(50))

        assertEquals(PriceBreakdown(24_000, 12_000, 12_000), (order.status as OrderStatus.Confirmed).price)

        order.reopen()
        system.addProductToOrder(1, 2, 1)
        order.confirm()

        assertEquals(PriceBreakdown(59_000, 0, 59_000), (order.status as OrderStatus.Confirmed).price)
        assertEquals(3, (order.status as OrderStatus.Confirmed).itemCount)

        order.pay("tx-1")

        assertEquals(59_000, system.calculatePaidRevenue())
        assertEquals(
            listOf(
                OrderEvent.ProductAdded(1, 2, 2),
                OrderEvent.OrderConfirmed(PriceBreakdown(24_000, 12_000, 12_000)),
                OrderEvent.OrderReopened,
                OrderEvent.ProductAdded(2, 1, 1),
                OrderEvent.OrderConfirmed(PriceBreakdown(59_000, 0, 59_000)),
                OrderEvent.OrderPaid("tx-1", 59_000)
            ),
            order.getEvents()
        )
    }

    @Test
    fun `failed operations leave the whole system untouched`() {
        val system = OrderSystem()
        system.catalog.add(coffee())

        val order = system.createOrder(1)
        system.addProductToOrder(1, 1, 2)

        val itemsBefore = order.getItems()
        val eventsBefore = order.getEvents()

        assertFailsWith<IllegalArgumentException> { system.createOrder(1) }
        assertFailsWith<NoSuchElementException> { system.addProductToOrder(1, 99) }
        assertFailsWith<NoSuchElementException> { system.addProductToOrder(99, 1) }
        assertFailsWith<IllegalArgumentException> { order.addProduct(coffee(), 0) }
        assertFailsWith<NoSuchElementException> { order.changeQuantity(99, 1) }
        assertFailsWith<IllegalArgumentException> { order.changeQuantity(1, 0) }
        assertFailsWith<NoSuchElementException> { order.removeProduct(99) }
        assertFailsWith<IllegalArgumentException> { order.confirm({ -1L }) }
        assertFailsWith<IllegalStateException> { order.pay("tx-1") }
        assertFailsWith<IllegalStateException> { order.reopen() }
        assertFailsWith<IllegalArgumentException> { order.cancel(" ") }

        assertEquals(itemsBefore, order.getItems())
        assertEquals(eventsBefore, order.getEvents())
        assertEquals(OrderStatus.Created, order.status)
        assertEquals(1, system.getOrders().size)
        assertEquals(listOf(coffee()), system.catalog.getAll())
        assertEquals(0, system.calculatePaidRevenue())
        assertEquals(emptyMap(), system.getSoldProducts())
    }

    @Test
    fun `terminal states reject every further transition`() {
        val system = OrderSystem()
        system.catalog.add(coffee())

        val paid = system.createOrder(1)
        system.addProductToOrder(1, 1)
        paid.confirm()
        paid.pay("tx-1")

        assertFailsWith<IllegalStateException> { paid.confirm() }
        assertFailsWith<IllegalStateException> { paid.reopen() }
        assertFailsWith<IllegalStateException> { paid.pay("tx-2") }
        assertFailsWith<IllegalStateException> { paid.cancel("late") }

        val cancelled = system.createOrder(2)
        system.addProductToOrder(2, 1)
        cancelled.cancel("stop")

        assertFailsWith<IllegalStateException> { cancelled.confirm() }
        assertFailsWith<IllegalStateException> { cancelled.reopen() }
        assertFailsWith<IllegalStateException> { cancelled.pay("tx-3") }
        assertFailsWith<IllegalStateException> { cancelled.cancel("again") }

        assertIs<OrderStatus.Paid>(paid.status)
        assertIs<OrderStatus.Cancelled>(cancelled.status)
    }

    @Test
    fun `catalog and orders stay independent`() {
        val system = OrderSystem()
        system.catalog.add(coffee())
        system.catalog.add(book())

        val order = system.createOrder(1)
        system.addProductToOrder(1, 1, 2)
        order.confirm()
        order.pay("tx-1")

        system.catalog.remove(1)
        system.catalog.remove(2)

        assertNull(system.catalog.get(1))
        assertEquals(emptyList(), system.catalog.getAll())
        assertEquals(listOf(OrderItem(coffee(), 2)), order.getItems())
        assertEquals(24_000, system.calculatePaidRevenue())
        assertEquals(mapOf(coffee() to 2), system.getSoldProducts())
    }

    @Test
    fun `callbacks are invoked exactly as contracted`() {
        val system = OrderSystem()
        system.catalog.add(coffee())

        val events = mutableListOf<OrderEvent>()
        val logs = mutableListOf<String>()
        var discountCalls = 0

        val order = system.createOrder(1) { events += it }
        system.addProductToOrder(1, 1, 2)

        val silent = system.createOrder(2)
        system.addProductToOrder(2, 1, 1)
        silent.confirm()

        order.confirm({ discountCalls++; 4_000L }) { logs += it }
        order.pay("tx-1")

        assertEquals(1, discountCalls)
        assertEquals(listOf("Order 1: subtotal=24000, discount=4000, total=20000"), logs)
        assertEquals(3, events.size)
        assertEquals(order.getEvents(), events)
        assertTrue(silent.getEvents().isNotEmpty())
    }

    @Test
    fun `empty system reports empty statistics`() {
        val system = OrderSystem()

        assertEquals(emptyList(), system.getOrders())
        assertEquals(emptyList(), system.findOrders { true })
        assertEquals(emptyList(), system.getOrdersByStatus(OrderStatusType.PAID))
        assertEquals(0, system.calculatePaidRevenue())
        assertEquals(emptyMap(), system.getSoldProducts())
        assertNull(system.getOrder(1))
        assertEquals(emptyList(), system.catalog.getAll())
    }
}
