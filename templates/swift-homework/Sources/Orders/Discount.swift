/// Скидка по списку позиций заказа, в копейках.
typealias Discount = ([OrderItem]) throws -> Int

func percentageDiscount(_ percent: Int) throws -> Discount {
    fatalError("TODO")
}

func fixedDiscount(_ amountInKopecks: Int) throws -> Discount {
    fatalError("TODO")
}

func categoryDiscount(_ category: ProductCategory, percent: Int) throws -> Discount {
    fatalError("TODO")
}

func combineDiscounts(_ discounts: Discount...) -> Discount {
    fatalError("TODO")
}
