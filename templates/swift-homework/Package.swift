// swift-tools-version: 5.10
// Манифест для локальных тестов. Чекер собирает Sources/Orders напрямую.
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
