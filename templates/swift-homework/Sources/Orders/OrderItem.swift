struct OrderItem: Hashable {
    let product: Product
    let quantity: Int

    init(product: Product, quantity: Int) throws {
        fatalError("TODO")
    }

    func subtotal() throws -> Int {
        fatalError("TODO")
    }
}
