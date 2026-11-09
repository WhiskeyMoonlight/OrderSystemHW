public struct OrderItem: Hashable {
    public let product: Product
    public let quantity: Int

    public init(product: Product, quantity: Int) throws {
        fatalError("TODO")
    }

    public func subtotal() throws -> Int {
        fatalError("TODO")
    }
}
