package orders

class ProductCatalog {

    private val products = LinkedHashMap<Int, Product>()

    fun add(product: Product) {
        require(!products.containsKey(product.id)) { "товар с id ${product.id} уже есть в каталоге" }
        products[product.id] = product
    }

    fun remove(productId: Int): Product =
        products.remove(productId) ?: throw NoSuchElementException("товара с id $productId нет в каталоге")

    fun get(productId: Int): Product? = products[productId]

    fun getAll(): List<Product> = products.values.toList()

    fun find(predicate: (Product) -> Boolean): List<Product> = products.values.filter(predicate)

    fun getByCategory(category: ProductCategory): List<Product> = find { it.category == category }
}
