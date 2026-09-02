package orders

sealed interface OrderEvent {

    data class ProductAdded(
        val productId: Int,
        val addedQuantity: Int,
        val resultingQuantity: Int
    ) : OrderEvent

    data class QuantityChanged(
        val productId: Int,
        val oldQuantity: Int,
        val newQuantity: Int
    ) : OrderEvent

    data class ProductRemoved(
        val productId: Int,
        val removedQuantity: Int
    ) : OrderEvent

    data object OrderCleared : OrderEvent

    data class OrderConfirmed(
        val price: PriceBreakdown
    ) : OrderEvent

    data object OrderReopened : OrderEvent

    data class OrderPaid(
        val transactionId: String,
        val paidPrice: Long
    ) : OrderEvent

    data class OrderCancelled(
        val reason: String,
        val previousStatus: OrderStatusType
    ) : OrderEvent
}
