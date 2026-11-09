import Foundation
import Glibc
import XCTest
@testable import CheckerXCTestPackageDiscoveredTests

final class ResultObserver: NSObject, XCTestObservation {
    private(set) var completed = 0
    private(set) var skipped = 0

    func testCaseDidFinish(_ testCase: XCTestCase) {
        guard let run = testCase.testRun else { return }
        completed += 1
        skipped += run.skipCount
    }
}

let tests = __allDiscoveredTests()
let expected = tests.reduce(0) { $0 + $1.allTests.count }
if CommandLine.arguments == [CommandLine.arguments[0], "--count"] {
    print(expected)
    exit(EXIT_SUCCESS)
}

guard CommandLine.arguments.count == 2, expected > 0 else {
    exit(EXIT_FAILURE)
}
let observer = ResultObserver()
XCTestObservationCenter.shared.addTestObserver(observer)
let status: CInt = XCTMain(tests, arguments: [CommandLine.arguments[0]])
guard status == EXIT_SUCCESS, observer.completed == expected, observer.skipped == 0 else {
    print("XCTest: завершено \(observer.completed)/\(expected), пропущено: \(observer.skipped)")
    exit(EXIT_FAILURE)
}
try "\(observer.completed)\n".write(toFile: CommandLine.arguments[1], atomically: true, encoding: .utf8)
