package orders

fun statusTypeOf(status: OrderStatus): OrderStatusType = when (status) {
    is OrderStatus.Created -> OrderStatusType.CREATED
    is OrderStatus.Confirmed -> OrderStatusType.CONFIRMED
    is OrderStatus.Paid -> OrderStatusType.PAID
    is OrderStatus.Cancelled -> OrderStatusType.CANCELLED
}

fun processOrder(order: Order): String = when (val status = order.status) {
    is OrderStatus.Created ->
        "Order ${order.id} is being edited"

    is OrderStatus.Confirmed ->
        "Order ${order.id} is confirmed: ${status.itemCount} items, total ${status.price.total}"

    is OrderStatus.Paid ->
        "Order ${order.id} is paid: transaction ${status.transactionId}, total ${status.paidPrice}"

    is OrderStatus.Cancelled ->
        "Order ${order.id} is cancelled from ${status.previousStatus}: ${status.reason}"
}
