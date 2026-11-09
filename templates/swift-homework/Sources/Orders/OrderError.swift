/// Аналог IllegalArgumentException / IllegalStateException из Kotlin-версии.
public enum OrderError: Error, Equatable {
    /// Некорректный аргумент: пустое имя, неположительная цена и т.п.
    case invalidArgument(String)
    /// Операция недопустима в текущем состоянии заказа.
    case invalidState(String)
}
