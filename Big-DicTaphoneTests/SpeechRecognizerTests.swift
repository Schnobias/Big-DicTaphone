import XCTest
@testable import Big_DicTaphone

final class SpeechRecognizerTests: XCTestCase {
    func testRecognitionCompletionResumesOnlyOnce() async throws {
        let value: String = try await withCheckedThrowingContinuation { (continuation: CheckedContinuation<String, Error>) in
            let completion = RecognitionCompletion(continuation)
            completion.finish(.success("first"))
            completion.finish(.success("second"))
        }

        XCTAssertEqual(value, "first")
    }
}
