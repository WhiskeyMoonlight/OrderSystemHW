package orders

internal fun product(
    id: Int = 1,
    name: String = "Product-$id",
    priceInKopecks: Long = 10_000,
    category: ProductCategory = ProductCategory.OTHER
): Product = Product(id, name, priceInKopecks, category)

internal fun coffee(): Product = product(1, "Coffee", 12_000, ProductCategory.FOOD)

internal fun book(): Product = product(2, "Kotlin in Action", 35_000, ProductCategory.BOOKS)

internal fun laptop(): Product = product(3, "Laptop", 8_999_900, ProductCategory.ELECTRONICS)

internal fun lamp(): Product = product(4, "Lamp", 4_599, ProductCategory.HOME)

internal fun orderOf(
    id: Int = 1,
    listener: ((OrderEvent) -> Unit)? = null,
    vararg items: Pair<Product, Int>
): Order = Order(id, listener).apply {
    items.forEach { (product, quantity) -> addProduct(product, quantity) }
}
