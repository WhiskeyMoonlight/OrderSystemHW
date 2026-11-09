public final class OrderSystem {
    public let catalog: ProductCatalog

    public init(catalog: ProductCatalog = ProductCatalog()) {
        self.catalog = catalog
    }

    @discardableResult
    public func createOrder(orderId: Int, eventListener: ((OrderEvent) -> Void)? = nil) throws -> Order {
        fatalError("TODO")
    }

    public func getOrder(orderId: Int) -> Order? {
        fatalError("TODO")
    }

    public func getOrders() -> [Order] {
        fatalError("TODO")
    }

    public func addProductToOrder(orderId: Int, productId: Int, quantity: Int = 1) throws {
        fatalError("TODO")
    }

    public func findOrders(_ predicate: (Order) -> Bool) -> [Order] {
        fatalError("TODO")
    }

    public func getOrdersByStatus(_ statusType: OrderStatusType) -> [Order] {
        fatalError("TODO")
    }

    public func calculatePaidRevenue() throws -> Int {
        fatalError("TODO")
    }

    public func getSoldProducts() -> [Product: Int] {
        fatalError("TODO")
    }
}
