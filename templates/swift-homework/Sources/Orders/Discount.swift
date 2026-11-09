/// Скидка по списку позиций заказа, в копейках.
public typealias Discount = ([OrderItem]) throws -> Int

public func percentageDiscount(_ percent: Int) throws -> Discount {
    fatalError("TODO")
}

public func fixedDiscount(_ amountInKopecks: Int) throws -> Discount {
    fatalError("TODO")
}

public func categoryDiscount(_ category: ProductCategory, percent: Int) throws -> Discount {
    fatalError("TODO")
}

public func combineDiscounts(_ discounts: Discount...) -> Discount {
    fatalError("TODO")
}
