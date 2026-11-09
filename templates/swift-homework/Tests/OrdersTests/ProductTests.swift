import XCTest
import Orders

final class ProductTests: XCTestCase {

    func testCreatesProductWithValidArguments() throws {
        let product = try Product(id: 1, name: "Coffee", priceInKopecks: 12_000, category: .food)

        XCTAssertEqual(product.id, 1)
        XCTAssertEqual(product.name, "Coffee")
        XCTAssertEqual(product.priceInKopecks, 12_000)
        XCTAssertEqual(product.category, .food)
    }

    func testAcceptsMaximumPrice() throws {
        XCTAssertEqual(try product(priceInKopecks: .max).priceInKopecks, .max)
    }

    func testRejectsZeroId() {
        assertInvalidArgument(try product(id: 0))
    }

    func testRejectsNegativeId() {
        assertInvalidArgument(try product(id: -5))
    }

    func testRejectsEmptyName() {
        assertInvalidArgument(try product(name: ""))
    }

    func testRejectsBlankName() {
        assertInvalidArgument(try product(name: "   "))
        assertInvalidArgument(try product(name: "\t\n "))
    }

    func testRejectsZeroPrice() {
        assertInvalidArgument(try product(priceInKopecks: 0))
    }

    func testRejectsNegativePrice() {
        assertInvalidArgument(try product(priceInKopecks: -1))
    }

    func testKeepsSurroundingWhitespaceInName() throws {
        XCTAssertEqual(try product(name: " Coffee ").name, " Coffee ")
    }

    func testEqualProductsHaveEqualHashValues() throws {
        let first = try Product(id: 1, name: "Coffee", priceInKopecks: 12_000, category: .food)
        let second = try Product(id: 1, name: "Coffee", priceInKopecks: 12_000, category: .food)

        XCTAssertEqual(first, second)
        XCTAssertEqual(first.hashValue, second.hashValue)
    }

    func testProductsWithDifferentFieldsAreNotEqual() throws {
        let base = try Product(id: 1, name: "Coffee", priceInKopecks: 12_000, category: .food)

        XCTAssertNotEqual(base, try Product(id: 2, name: "Coffee", priceInKopecks: 12_000, category: .food))
        XCTAssertNotEqual(base, try Product(id: 1, name: "Tea", priceInKopecks: 12_000, category: .food))
        XCTAssertNotEqual(base, try Product(id: 1, name: "Coffee", priceInKopecks: 12_001, category: .food))
        XCTAssertNotEqual(base, try Product(id: 1, name: "Coffee", priceInKopecks: 12_000, category: .other))
    }
}
