package orders

class ProductCatalog {

    fun add(product: Product) {
        TODO()
    }

    fun remove(productId: Int): Product {
        TODO()
    }

    fun get(productId: Int): Product? {
        TODO()
    }

    fun getAll(): List<Product> {
        TODO()
    }

    fun find(predicate: (Product) -> Boolean): List<Product> {
        TODO()
    }

    fun getByCategory(category: ProductCategory): List<Product> {
        TODO()
    }
}
