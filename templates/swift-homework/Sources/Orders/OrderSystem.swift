final class OrderSystem {
    let catalog: ProductCatalog

    init(catalog: ProductCatalog = ProductCatalog()) {
        self.catalog = catalog
    }

    @discardableResult
    func createOrder(orderId: Int, eventListener: ((OrderEvent) -> Void)? = nil) throws -> Order {
        fatalError("TODO")
    }

    func getOrder(orderId: Int) -> Order? {
        fatalError("TODO")
    }

    func getOrders() -> [Order] {
        fatalError("TODO")
    }

    func addProductToOrder(orderId: Int, productId: Int, quantity: Int = 1) throws {
        fatalError("TODO")
    }

    func findOrders(_ predicate: (Order) -> Bool) -> [Order] {
        fatalError("TODO")
    }

    func getOrdersByStatus(_ statusType: OrderStatusType) -> [Order] {
        fatalError("TODO")
    }

    func calculatePaidRevenue() throws -> Int {
        fatalError("TODO")
    }

    func getSoldProducts() -> [Product: Int] {
        fatalError("TODO")
    }
}
