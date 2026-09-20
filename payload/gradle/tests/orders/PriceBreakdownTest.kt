package orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class PriceBreakdownTest {

    @Test
    fun `creates consistent breakdown`() {
        val price = PriceBreakdown(10_000, 1_500, 8_500)

        assertEquals(10_000, price.subtotal)
        assertEquals(1_500, price.discount)
        assertEquals(8_500, price.total)
    }

    @Test
    fun `allows zero values`() {
        assertEquals(0, PriceBreakdown(0, 0, 0).total)
    }

    @Test
    fun `allows discount equal to subtotal`() {
        assertEquals(0, PriceBreakdown(10_000, 10_000, 0).total)
    }

    @Test
    fun `rejects negative subtotal`() {
        assertFailsWith<IllegalArgumentException> { PriceBreakdown(-1, 0, -1) }
    }

    @Test
    fun `rejects negative discount`() {
        assertFailsWith<IllegalArgumentException> { PriceBreakdown(100, -1, 101) }
    }

    @Test
    fun `rejects discount greater than subtotal`() {
        assertFailsWith<IllegalArgumentException> { PriceBreakdown(100, 200, -100) }
    }

    @Test
    fun `rejects inconsistent total`() {
        assertFailsWith<IllegalArgumentException> { PriceBreakdown(100, 20, 90) }
    }

    @Test
    fun `rejects inconsistent total without discount`() {
        assertFailsWith<IllegalArgumentException> { PriceBreakdown(100, 0, 100_000) }
    }

    @Test
    fun `breakdowns are compared structurally`() {
        assertEquals(PriceBreakdown(100, 10, 90), PriceBreakdown(100, 10, 90))
    }
}
