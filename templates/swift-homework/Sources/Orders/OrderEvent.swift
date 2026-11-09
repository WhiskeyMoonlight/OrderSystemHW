public enum OrderEvent: Equatable {
    case productAdded(productId: Int, addedQuantity: Int, resultingQuantity: Int)
    case quantityChanged(productId: Int, oldQuantity: Int, newQuantity: Int)
    case productRemoved(productId: Int, removedQuantity: Int)
    case orderCleared
    case orderConfirmed(price: PriceBreakdown)
    case orderReopened
    case orderPaid(transactionId: String, paidPrice: Int)
    case orderCancelled(reason: String, previousStatus: OrderStatusType)
}
