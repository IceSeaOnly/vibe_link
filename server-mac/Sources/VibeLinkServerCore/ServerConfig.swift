import Foundation

public struct ServerConfig: Sendable {
    public let host: String
    public let port: UInt16
    public let adminPort: UInt16
    public let token: String

    public init(port: UInt16 = 8765, adminPort: UInt16? = nil, token: String = ServerConfig.generateToken(), host: String = "0.0.0.0") {
        self.host = host
        self.port = port
        self.adminPort = adminPort ?? port.addingReportingOverflow(1).partialValue
        self.token = token
    }

    public init(arguments: [String]) throws {
        var port: UInt16 = 8765
        var adminPort: UInt16?
        var token: String?
        var index = 1

        while index < arguments.count {
            let argument = arguments[index]
            switch argument {
            case "--port":
                guard index + 1 < arguments.count, let parsed = UInt16(arguments[index + 1]) else {
                    throw ConfigError.invalidPort
                }
                port = parsed
                index += 2
            case "--admin-port":
                guard index + 1 < arguments.count, let parsed = UInt16(arguments[index + 1]) else {
                    throw ConfigError.invalidAdminPort
                }
                adminPort = parsed
                index += 2
            case "--token":
                guard index + 1 < arguments.count, !arguments[index + 1].isEmpty else {
                    throw ConfigError.invalidToken
                }
                token = arguments[index + 1]
                index += 2
            case "--help", "-h":
                throw ConfigError.helpRequested
            default:
                throw ConfigError.unknownArgument(argument)
            }
        }

        self.init(port: port, adminPort: adminPort, token: token ?? ServerConfig.generateToken())
    }

    public static func generateToken() -> String {
        UUID().uuidString.replacingOccurrences(of: "-", with: "").lowercased()
    }
}

public enum ConfigError: LocalizedError {
    case invalidPort
    case invalidAdminPort
    case invalidToken
    case unknownArgument(String)
    case helpRequested

    public var errorDescription: String? {
        switch self {
        case .invalidPort:
            return "--port requires a value from 0 to 65535"
        case .invalidAdminPort:
            return "--admin-port requires a value from 0 to 65535"
        case .invalidToken:
            return "--token requires a non-empty value"
        case .unknownArgument(let value):
            return "Unknown argument: \(value)"
        case .helpRequested:
            return nil
        }
    }
}
