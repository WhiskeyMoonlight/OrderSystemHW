package orders

enum class OrderStatusType {
    CREATED,
    CONFIRMED,
    PAID,
    CANCELLED
}

sealed interface OrderStatus {

    data object Created : OrderStatus

    data class Confirmed(
        val price: PriceBreakdown,
        val itemCount: Int
    ) : OrderStatus

    data class Paid(
        val transactionId: String,
        val paidPrice: Long
    ) : OrderStatus

    data class Cancelled(
        val reason: String,
        val previousStatus: OrderStatusType
    ) : OrderStatus
}
