import XCTest
@testable import Orders

func product(
    id: Int = 1,
    name: String? = nil,
    priceInKopecks: Int = 10_000,
    category: ProductCategory = .other
) throws -> Product {
    try Product(id: id, name: name ?? "Product-\(id)", priceInKopecks: priceInKopecks, category: category)
}

func coffee() throws -> Product { try product(id: 1, name: "Coffee", priceInKopecks: 12_000, category: .food) }
func book() throws -> Product { try product(id: 2, name: "Swift in Depth", priceInKopecks: 35_000, category: .books) }
func laptop() throws -> Product { try product(id: 3, name: "Laptop", priceInKopecks: 8_999_900, category: .electronics) }
func lamp() throws -> Product { try product(id: 4, name: "Lamp", priceInKopecks: 4_599, category: .home) }

func orderOf(
    id: Int = 1,
    listener: ((OrderEvent) -> Void)? = nil,
    _ items: (Product, Int)...
) throws -> Order {
    let order = try Order(id: id, eventListener: listener)
    for (product, quantity) in items {
        try order.addProduct(product, quantity: quantity)
    }
    return order
}

/// Аналог assertFailsWith<IllegalArgumentException>.
func assertInvalidArgument<T>(
    _ expression: @autoclosure () throws -> T,
    file: StaticString = #filePath,
    line: UInt = #line
) {
    XCTAssertThrowsError(try expression(), file: file, line: line) { error in
        guard case .invalidArgument = error as? OrderError else {
            return XCTFail("ожидалась OrderError.invalidArgument, получено \(error)", file: file, line: line)
        }
    }
}

/// Аналог assertFailsWith<IllegalStateException>.
func assertInvalidState<T>(
    _ expression: @autoclosure () throws -> T,
    file: StaticString = #filePath,
    line: UInt = #line
) {
    XCTAssertThrowsError(try expression(), file: file, line: line) { error in
        guard case .invalidState = error as? OrderError else {
            return XCTFail("ожидалась OrderError.invalidState, получено \(error)", file: file, line: line)
        }
    }
}
