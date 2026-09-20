package orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class OrderSystemTest {

    private fun systemWithCatalog(): OrderSystem = OrderSystem().apply {
        catalog.add(coffee())
        catalog.add(book())
        catalog.add(laptop())
    }

    @Test
    fun `new system has empty catalog and no orders`() {
        val system = OrderSystem()

        assertEquals(emptyList(), system.catalog.getAll())
        assertEquals(emptyList(), system.getOrders())
    }

    @Test
    fun `accepts an externally built catalog`() {
        val catalog = ProductCatalog()
        catalog.add(coffee())
        val system = OrderSystem(catalog)

        assertSame(catalog, system.catalog)
        assertEquals(listOf(coffee()), system.catalog.getAll())
    }

    @Test
    fun `createOrder stores and returns the order`() {
        val system = OrderSystem()
        val order = system.createOrder(101)

        assertEquals(101, order.id)
        assertSame(order, system.getOrder(101))
        assertEquals(listOf(order), system.getOrders())
    }

    @Test
    fun `createOrder rejects duplicate id`() {
        val system = OrderSystem()
        system.createOrder(101)

        assertFailsWith<IllegalArgumentException> { system.createOrder(101) }
        assertEquals(1, system.getOrders().size)
    }

    @Test
    fun `createOrder delegates id validation to the order`() {
        val system = OrderSystem()

        assertFailsWith<IllegalArgumentException> { system.createOrder(0) }
        assertEquals(emptyList(), system.getOrders())
    }

    @Test
    fun `createOrder wires the event listener`() {
        val system = systemWithCatalog()
        val events = mutableListOf<OrderEvent>()
        system.createOrder(101) { events += it }

        system.addProductToOrder(101, 1, 2)

        assertEquals(listOf<OrderEvent>(OrderEvent.ProductAdded(1, 2, 2)), events.toList())
    }

    @Test
    fun `getOrder returns null for unknown id`() {
        val system = OrderSystem()
        system.createOrder(101)

        assertNull(system.getOrder(999))
    }

    @Test
    fun `getOrders keeps creation order`() {
        val system = OrderSystem()
        system.createOrder(30)
        system.createOrder(10)
        system.createOrder(20)

        assertEquals(listOf(30, 10, 20), system.getOrders().map { it.id })
    }

    @Test
    fun `getOrders does not expose internal storage`() {
        val system = OrderSystem()
        system.createOrder(101)

        val snapshot = system.getOrders()
        runCatching { (snapshot as MutableList<*>).clear() }

        assertEquals(1, system.getOrders().size)
    }

    @Test
    fun `addProductToOrder adds catalog product with default quantity`() {
        val system = systemWithCatalog()
        val order = system.createOrder(101)

        system.addProductToOrder(101, 2)

        assertEquals(listOf(OrderItem(book(), 1)), order.getItems())
    }

    @Test
    fun `addProductToOrder adds requested quantity`() {
        val system = systemWithCatalog()
        val order = system.createOrder(101)

        system.addProductToOrder(101, 1, 4)

        assertEquals(listOf(OrderItem(coffee(), 4)), order.getItems())
    }

    @Test
    fun `addProductToOrder fails for unknown order`() {
        val system = systemWithCatalog()

        assertFailsWith<NoSuchElementException> { system.addProductToOrder(999, 1) }
    }

    @Test
    fun `addProductToOrder fails for unknown product`() {
        val system = systemWithCatalog()
        val order = system.createOrder(101)

        assertFailsWith<NoSuchElementException> { system.addProductToOrder(101, 999) }
        assertEquals(emptyList(), order.getItems())
    }

    @Test
    fun `addProductToOrder fails when both order and product are unknown`() {
        val system = systemWithCatalog()

        assertFailsWith<NoSuchElementException> { system.addProductToOrder(999, 999) }
    }

    @Test
    fun `addProductToOrder respects order state`() {
        val system = systemWithCatalog()
        val order = system.createOrder(101)
        system.addProductToOrder(101, 1)
        order.confirm()

        assertFailsWith<IllegalStateException> { system.addProductToOrder(101, 2) }
    }

    @Test
    fun `addProductToOrder validates quantity`() {
        val system = systemWithCatalog()
        system.createOrder(101)

        assertFailsWith<IllegalArgumentException> { system.addProductToOrder(101, 1, 0) }
    }

    @Test
    fun `findOrders filters and keeps creation order`() {
        val system = systemWithCatalog()
        system.createOrder(1)
        system.createOrder(2)
        system.createOrder(3)
        system.addProductToOrder(2, 1)
        system.addProductToOrder(3, 2)

        assertEquals(listOf(2, 3), system.findOrders { it.getItems().isNotEmpty() }.map { it.id })
    }

    @Test
    fun `findOrders returns empty list when nothing matches`() {
        val system = OrderSystem()
        system.createOrder(1)

        assertEquals(emptyList(), system.findOrders { false })
    }

    @Test
    fun `findOrders on empty system returns empty list`() {
        assertEquals(emptyList(), OrderSystem().findOrders { true })
    }

    @Test
    fun `getOrdersByStatus uses the actual current status`() {
        val system = systemWithCatalog()
        val created = system.createOrder(1)
        val confirmed = system.createOrder(2)
        val paid = system.createOrder(3)
        val cancelled = system.createOrder(4)

        system.addProductToOrder(1, 1)
        system.addProductToOrder(2, 1)
        system.addProductToOrder(3, 1)
        system.addProductToOrder(4, 1)

        confirmed.confirm()
        paid.confirm()
        paid.pay("tx-1")
        cancelled.cancel("stop")

        assertEquals(listOf(created), system.getOrdersByStatus(OrderStatusType.CREATED))
        assertEquals(listOf(confirmed), system.getOrdersByStatus(OrderStatusType.CONFIRMED))
        assertEquals(listOf(paid), system.getOrdersByStatus(OrderStatusType.PAID))
        assertEquals(listOf(cancelled), system.getOrdersByStatus(OrderStatusType.CANCELLED))
    }

    @Test
    fun `getOrdersByStatus reacts to status changes`() {
        val system = systemWithCatalog()
        val order = system.createOrder(1)
        system.addProductToOrder(1, 1)

        assertEquals(listOf(order), system.getOrdersByStatus(OrderStatusType.CREATED))

        order.confirm()

        assertEquals(emptyList(), system.getOrdersByStatus(OrderStatusType.CREATED))
        assertEquals(listOf(order), system.getOrdersByStatus(OrderStatusType.CONFIRMED))
    }

    @Test
    fun `revenue of empty system is zero`() {
        assertEquals(0, OrderSystem().calculatePaidRevenue())
    }

    @Test
    fun `revenue counts only paid orders`() {
        val system = systemWithCatalog()
        system.createOrder(1)
        val confirmed = system.createOrder(2)
        val firstPaid = system.createOrder(3)
        val secondPaid = system.createOrder(4)
        val cancelled = system.createOrder(5)

        system.addProductToOrder(1, 1)
        system.addProductToOrder(2, 1)
        system.addProductToOrder(3, 1, 2)
        system.addProductToOrder(4, 2)
        system.addProductToOrder(5, 3)

        confirmed.confirm()
        firstPaid.confirm()
        firstPaid.pay("tx-1")
        secondPaid.confirm(percentageDiscount(10))
        secondPaid.pay("tx-2")
        cancelled.cancel("stop")

        assertEquals(24_000 + 31_500, system.calculatePaidRevenue())
    }

    @Test
    fun `revenue is zero when nothing is paid`() {
        val system = systemWithCatalog()
        val order = system.createOrder(1)
        system.addProductToOrder(1, 1)
        order.confirm()

        assertEquals(0, system.calculatePaidRevenue())
    }

    @Test
    fun `revenue fails on overflow`() {
        val system = OrderSystem()
        system.catalog.add(product(id = 1, priceInKopecks = Long.MAX_VALUE))

        val first = system.createOrder(1)
        system.addProductToOrder(1, 1)
        first.confirm()
        first.pay("tx-1")

        val second = system.createOrder(2)
        system.addProductToOrder(2, 1)
        second.confirm()
        second.pay("tx-2")

        assertFailsWith<ArithmeticException> { system.calculatePaidRevenue() }
    }

    @Test
    fun `sold products of empty system is empty map`() {
        assertEquals(emptyMap(), OrderSystem().getSoldProducts())
    }

    @Test
    fun `sold products counts only paid orders`() {
        val system = systemWithCatalog()
        val paid = system.createOrder(1)
        val confirmed = system.createOrder(2)
        val cancelled = system.createOrder(3)

        system.addProductToOrder(1, 1, 2)
        system.addProductToOrder(2, 2, 5)
        system.addProductToOrder(3, 3, 7)

        paid.confirm()
        paid.pay("tx-1")
        confirmed.confirm()
        cancelled.cancel("stop")

        assertEquals(mapOf(coffee() to 2), system.getSoldProducts())
    }

    @Test
    fun `sold products merges equal products across orders`() {
        val system = systemWithCatalog()
        val first = system.createOrder(1)
        val second = system.createOrder(2)

        system.addProductToOrder(1, 1, 2)
        system.addProductToOrder(1, 2, 1)
        system.addProductToOrder(2, 1, 3)

        first.confirm()
        first.pay("tx-1")
        second.confirm()
        second.pay("tx-2")

        assertEquals(mapOf(coffee() to 5, book() to 1), system.getSoldProducts())
    }

    @Test
    fun `sold products keeps first appearance order`() {
        val system = systemWithCatalog()
        val first = system.createOrder(1)
        val second = system.createOrder(2)

        system.addProductToOrder(1, 3, 1)
        system.addProductToOrder(1, 1, 1)
        system.addProductToOrder(2, 2, 1)
        system.addProductToOrder(2, 3, 1)

        first.confirm()
        first.pay("tx-1")
        second.confirm()
        second.pay("tx-2")

        assertEquals(listOf(laptop(), coffee(), book()), system.getSoldProducts().keys.toList())
        assertEquals(listOf(2, 1, 1), system.getSoldProducts().values.toList())
    }

    @Test
    fun `sold products fails on quantity overflow`() {
        val system = OrderSystem()
        system.catalog.add(product(id = 1, priceInKopecks = 1))

        val first = system.createOrder(1)
        system.addProductToOrder(1, 1, Int.MAX_VALUE)
        first.confirm()
        first.pay("tx-1")

        val second = system.createOrder(2)
        system.addProductToOrder(2, 1, Int.MAX_VALUE)
        second.confirm()
        second.pay("tx-2")

        assertFailsWith<ArithmeticException> { system.getSoldProducts() }
    }

    @Test
    fun `catalog changes do not affect existing orders`() {
        val system = systemWithCatalog()
        val order = system.createOrder(1)
        system.addProductToOrder(1, 1, 2)

        system.catalog.remove(1)
        system.catalog.add(product(id = 1, name = "Tea", priceInKopecks = 999, category = ProductCategory.FOOD))

        assertEquals(listOf(OrderItem(coffee(), 2)), order.getItems())
        assertEquals(24_000, order.calculateSubtotal())
    }
}
