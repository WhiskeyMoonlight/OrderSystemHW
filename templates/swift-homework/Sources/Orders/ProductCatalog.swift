public final class ProductCatalog {
    public init() {}

    public func add(_ product: Product) throws {
        fatalError("TODO")
    }

    @discardableResult
    public func remove(productId: Int) throws -> Product {
        fatalError("TODO")
    }

    public func get(productId: Int) -> Product? {
        fatalError("TODO")
    }

    public func getAll() -> [Product] {
        fatalError("TODO")
    }

    public func find(_ predicate: (Product) -> Bool) -> [Product] {
        fatalError("TODO")
    }

    public func getByCategory(_ category: ProductCategory) -> [Product] {
        fatalError("TODO")
    }
}
