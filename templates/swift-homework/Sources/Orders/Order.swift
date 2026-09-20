protocol PriceCalculator {
    func calculateSubtotal() throws -> Int
}

final class Order: PriceCalculator {
    let id: Int
    private(set) var status: OrderStatus = .created
    private let eventListener: ((OrderEvent) -> Void)?

    init(id: Int, eventListener: ((OrderEvent) -> Void)? = nil) throws {
        fatalError("TODO")
    }

    func getItems() -> [OrderItem] {
        fatalError("TODO")
    }

    func getEvents() -> [OrderEvent] {
        fatalError("TODO")
    }

    func addProduct(_ product: Product, quantity: Int = 1) throws {
        fatalError("TODO")
    }

    func changeQuantity(productId: Int, newQuantity: Int) throws {
        fatalError("TODO")
    }

    @discardableResult
    func removeProduct(productId: Int) throws -> OrderItem {
        fatalError("TODO")
    }

    func clear() throws {
        fatalError("TODO")
    }

    func findItem(productId: Int) -> OrderItem? {
        fatalError("TODO")
    }

    func calculateSubtotal() throws -> Int {
        fatalError("TODO")
    }

    func confirm(discount: Discount? = nil, discountLogger: ((String) -> Void)? = nil) throws {
        fatalError("TODO")
    }

    func reopen() throws {
        fatalError("TODO")
    }

    func pay(transactionId: String) throws {
        fatalError("TODO")
    }

    func cancel(reason: String) throws {
        fatalError("TODO")
    }
}
