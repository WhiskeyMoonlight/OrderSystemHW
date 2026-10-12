enum ProductCategory: CaseIterable {
    case food
    case electronics
    case books
    case home
    case other
}

/// Цена хранится в копейках целым числом: Decimal на Linux ведёт себя иначе, чем на macOS.
struct Product: Hashable {
    let id: Int
    let name: String
    let priceInKopecks: Int
    let category: ProductCategory

    init(id: Int, name: String, priceInKopecks: Int, category: ProductCategory) throws {
        fatalError("TODO")
    }
}
