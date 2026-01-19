import Foundation

struct Stakeholder: Codable, Identifiable, Equatable {
    let id: UUID
    var name: String
    var email: String
    var role: String?
    
    init(id: UUID = UUID(), name: String, email: String, role: String? = nil) {
        self.id = id
        self.name = name
        self.email = email
        self.role = role
    }
}
