import Foundation

public struct HealthResponse: Codable {
    public let ok: Bool
    public let name: String
    public let version: String
    public let streamUrl: String
    public let lowLatencyStreamUrl: String
    public let videoStreamUrl: String
    public let screen: ScreenInfo
    public let displays: [DisplayInfo]
}

public struct PermissionStatus: Codable, Sendable {
    public let id: String
    public let name: String
    public let granted: Bool
    public let required: Bool
    public let guidance: String
}

public struct PermissionsResponse: Codable, Sendable {
    public let ok: Bool
    public let permissions: [PermissionStatus]
}

public struct PairingInfo: Codable, Sendable {
    public let appName: String
    public let version: String
    public let serverUrls: [String]
    public let adminUrls: [String]
    public let token: String
    public let pairingUri: String
}

public struct ScreenInfo: Codable, Sendable {
    public let width: Int
    public let height: Int
    public let scale: Double
}

public struct DisplayInfo: Codable, Sendable {
    public let id: String
    public let name: String
    public let x: Int
    public let y: Int
    public let width: Int
    public let height: Int
    public let scale: Double
    public let isMain: Bool
}

public enum CaptureSourceType: String, Codable, Sendable {
    case display
    case window
}

public struct CaptureSource: Codable, Sendable, Equatable {
    public let id: String
    public let type: CaptureSourceType
    public let name: String
    public let appName: String?
    public let x: Int
    public let y: Int
    public let width: Int
    public let height: Int
    public let scale: Double
    public let isMain: Bool

    public init(id: String, type: CaptureSourceType, name: String, appName: String?, x: Int, y: Int, width: Int, height: Int, scale: Double, isMain: Bool) {
        self.id = id
        self.type = type
        self.name = name
        self.appName = appName
        self.x = x
        self.y = y
        self.width = width
        self.height = height
        self.scale = scale
        self.isMain = isMain
    }
}

public struct CaptureSourcesResponse: Codable, Sendable {
    public let ok: Bool
    public let sources: [CaptureSource]
    public let selected: CaptureSource?
}

public struct CaptureSourceSelectionRequest: Codable, Sendable {
    public let id: String

    public init(id: String) {
        self.id = id
    }
}

public struct OKResponse: Codable {
    public let ok: Bool
}

public struct ErrorResponse: Codable {
    public let ok: Bool
    public let error: String
}

public struct QuickText: Codable, Sendable {
    public let id: String
    public let name: String
    public let group: String
    public let content: String
}

public struct ShortcutButton: Codable, Sendable {
    public let id: String
    public let name: String
    public let x: Double
    public let y: Double
    public let screenWidth: Double
    public let screenHeight: Double
    public let requiresConfirmation: Bool
}

public struct PresetCommand: Codable, Sendable {
    public let id: String
    public let name: String
    public let command: String
    public let workingDirectory: String
    public let requiresConfirmation: Bool
}

public struct RunCommandRequest: Codable {
    public let id: String
}

public struct RunCommandResponse: Codable {
    public let ok: Bool
    public let runId: String
    public let status: CommandRunStatus
}

public enum CommandRunStatus: String, Codable, Sendable {
    case running
    case succeeded
    case failed
}

public struct CommandRunRecord: Codable, Sendable {
    public let id: String
    public let commandId: String
    public var status: CommandRunStatus
    public var exitCode: Int32?
    public var output: String
    public let startedAt: Date
    public var finishedAt: Date?
}

public struct ControlRequest: Codable {
    public let type: String
    public let displayId: String?
    public let x: Double?
    public let y: Double?
    public let fromX: Double?
    public let fromY: Double?
    public let toX: Double?
    public let toY: Double?
    public let durationMs: Int?
    public let deltaX: Int32?
    public let deltaY: Int32?
    public let text: String?
    public let submit: Bool?
    public let screenWidth: Double?
    public let screenHeight: Double?
}

public struct RunShortcutButtonRequest: Codable {
    public let id: String
}
