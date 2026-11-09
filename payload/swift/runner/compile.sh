#!/usr/bin/env bash
set -euo pipefail

mkdir -p abi payload
mapfile -d '' sources < <(find Orders -type f -name '*.swift' -print0)
swiftc -swift-version 5 -package-name Orders -parse-as-library -enable-library-evolution \
  -emit-library -emit-module -module-name Orders -emit-module-path abi/Orders.swiftmodule \
  -o abi/libOrders.so "${sources[@]}"

# SwiftPM находит XCTest автоматически, но линкует их с внешней динамической
# библиотекой. В Go-бинарник попадёт раннер, а не библиотека из заглушек.
swift build --build-tests --enable-test-discovery --experimental-test-entry-point-path "$PWD/Runner.swift" \
  -Xswiftc -I -Xswiftc "$PWD/abi" -Xlinker "-L$PWD/abi" -Xlinker -lOrders \
  -Xlinker -rpath -Xlinker '$ORIGIN'
runner="$(swift build --show-bin-path)/CheckerXCTestPackageTests.xctest"
LD_LIBRARY_PATH="$PWD/abi" ldd "$runner" | grep -F "libOrders.so => $PWD/abi/libOrders.so"
count="$(LD_LIBRARY_PATH="$PWD/abi" "$runner" --count)"
[[ "$count" =~ ^[1-9][0-9]*$ ]] || { echo "XCTest не обнаружил тестов" >&2; exit 1; }
echo "Собрано эталонных XCTest: $count"
cp "$runner" payload/OrdersTests.xctest
printf '%s\n' "$count" > payload/test-count
