public enum ProductCategory: CaseIterable {
    case food
    case electronics
    case books
    case home
    case other
}

/// Цена хранится в копейках целым числом: Decimal на Linux ведёт себя иначе, чем на macOS.
public struct Product: Hashable {
    public let id: Int
    public let name: String
    public let priceInKopecks: Int
    public let category: ProductCategory

    public init(id: Int, name: String, priceInKopecks: Int, category: ProductCategory) throws {
        fatalError("TODO")
    }
}
