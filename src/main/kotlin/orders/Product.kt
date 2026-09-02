package orders

enum class ProductCategory {
    FOOD,
    ELECTRONICS,
    BOOKS,
    HOME,
    OTHER
}

data class Product(
    val id: Int,
    val name: String,
    val priceInKopecks: Long,
    val category: ProductCategory
) {
    init {
        TODO()
    }
}
