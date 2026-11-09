public protocol PriceCalculator {
    func calculateSubtotal() throws -> Int
}

public final class Order: PriceCalculator {
    public let id: Int
    public private(set) var status: OrderStatus = .created
    private let eventListener: ((OrderEvent) -> Void)?

    public init(id: Int, eventListener: ((OrderEvent) -> Void)? = nil) throws {
        fatalError("TODO")
    }

    public func getItems() -> [OrderItem] {
        fatalError("TODO")
    }

    public func getEvents() -> [OrderEvent] {
        fatalError("TODO")
    }

    public func addProduct(_ product: Product, quantity: Int = 1) throws {
        fatalError("TODO")
    }

    public func changeQuantity(productId: Int, newQuantity: Int) throws {
        fatalError("TODO")
    }

    @discardableResult
    public func removeProduct(productId: Int) throws -> OrderItem {
        fatalError("TODO")
    }

    public func clear() throws {
        fatalError("TODO")
    }

    public func findItem(productId: Int) -> OrderItem? {
        fatalError("TODO")
    }

    public func calculateSubtotal() throws -> Int {
        fatalError("TODO")
    }

    public func confirm(discount: Discount? = nil, discountLogger: ((String) -> Void)? = nil) throws {
        fatalError("TODO")
    }

    public func reopen() throws {
        fatalError("TODO")
    }

    public func pay(transactionId: String) throws {
        fatalError("TODO")
    }

    public func cancel(reason: String) throws {
        fatalError("TODO")
    }
}
