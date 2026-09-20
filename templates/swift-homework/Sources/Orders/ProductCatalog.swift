final class ProductCatalog {
    init() {}

    func add(_ product: Product) throws {
        fatalError("TODO")
    }

    @discardableResult
    func remove(productId: Int) throws -> Product {
        fatalError("TODO")
    }

    func get(productId: Int) -> Product? {
        fatalError("TODO")
    }

    func getAll() -> [Product] {
        fatalError("TODO")
    }

    func find(_ predicate: (Product) -> Bool) -> [Product] {
        fatalError("TODO")
    }

    func getByCategory(_ category: ProductCategory) -> [Product] {
        fatalError("TODO")
    }
}
