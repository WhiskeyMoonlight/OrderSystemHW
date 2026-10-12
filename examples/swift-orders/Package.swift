// swift-tools-version: 5.10
// Этот файл закреплён: при проверке чекер перезаписывает его эталонной версией.
// Не добавляйте зависимости и таргеты — в CI их не будет.
import PackageDescription

let package = Package(
    name: "Orders",
    products: [
        .library(name: "Orders", targets: ["Orders"]),
    ],
    targets: [
        .target(name: "Orders", path: "Sources/Orders"),
        .testTarget(name: "OrdersTests", dependencies: ["Orders"], path: "Tests/OrdersTests"),
    ]
)
