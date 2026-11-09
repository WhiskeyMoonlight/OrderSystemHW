public enum OrderStatusType: CaseIterable {
    case created
    case confirmed
    case paid
    case cancelled
}

public enum OrderStatus: Equatable {
    case created
    case confirmed(price: PriceBreakdown, itemCount: Int)
    case paid(transactionId: String, paidPrice: Int)
    case cancelled(reason: String, previousStatus: OrderStatusType)
}
