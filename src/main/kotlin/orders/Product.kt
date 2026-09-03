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
        require(id > 0) { "id товара должен быть положительным, получено $id" }
        require(name.isNotBlank()) { "название товара не может быть пустым" }
        require(priceInKopecks > 0) { "цена должна быть положительной, получено $priceInKopecks" }
    }
}
