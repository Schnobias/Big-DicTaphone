//
//  SMTPService.swift
//  Big-DicTaphone
//
//  Created by ANTIGRAVITY on 2026-01-16.
//

import Foundation
import SwiftSMTP

actor SMTPService {
    static let shared = SMTPService()
    
    private init() {}
    
    func sendEmail(host: String, port: Int, user: String, pass: String, to: String, subject: String, body: String) async throws {
        let smtp = SMTP(
            hostname: host,
            email: user,
            password: pass,
            port: Int32(port),
            tlsMode: .requireSTARTTLS, // Most common for Gmail/Outlook
            tlsConfiguration: nil,
            authMethods: [], // library defaults to suitable ones
            domainName: "localhost",
            timeout: 10
        )
        
        let fromUser = Mail.User(name: "Big DicTa", email: user)
        let toUser = Mail.User(name: to, email: to)
        
        // Although the library has an `Attachment` type, we are sending plain text body for now.
        // We can construct a Mail object.
        
        let mail = Mail(
            from: fromUser,
            to: [toUser],
            subject: subject,
            text: body
        )
        
        return try await withCheckedThrowingContinuation { continuation in
            smtp.send(mail) { error in
                if let error = error {
                    continuation.resume(throwing: error)
                } else {
                    continuation.resume()
                }
            }
        }
    }
}
