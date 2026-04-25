import Foundation

public struct ControlButton: Codable, Sendable {
    public let id: String
    public let label: String
    public let type: String
}

public struct ClientConfig: Codable, Sendable {
    public var controlButtons: [ControlButton]
    public var quickTexts: [QuickText]
    public var commands: [PresetCommand]
    public var shortcutButtons: [ShortcutButton]
    public var updatedAt: Date

    public init(controlButtons: [ControlButton], quickTexts: [QuickText], commands: [PresetCommand], shortcutButtons: [ShortcutButton], updatedAt: Date) {
        self.controlButtons = controlButtons
        self.quickTexts = quickTexts
        self.commands = commands
        self.shortcutButtons = shortcutButtons
        self.updatedAt = updatedAt
    }

    private enum CodingKeys: String, CodingKey {
        case controlButtons
        case quickTexts
        case commands
        case shortcutButtons
        case updatedAt
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        controlButtons = try container.decode([ControlButton].self, forKey: .controlButtons)
        quickTexts = try container.decode([QuickText].self, forKey: .quickTexts)
        commands = try container.decodeIfPresent([PresetCommand].self, forKey: .commands) ?? AdminConfigStore.defaultCommands()
        shortcutButtons = try container.decodeIfPresent([ShortcutButton].self, forKey: .shortcutButtons) ?? AdminConfigStore.defaultShortcutButtons()
        updatedAt = try container.decode(Date.self, forKey: .updatedAt)
    }
}

public final class AdminConfigStore: @unchecked Sendable {
    private let url: URL
    private let lock = NSLock()
    private var config: ClientConfig

    public init(url: URL = AdminConfigStore.defaultURL()) {
        self.url = url
        self.config = Self.load(from: url)
    }

    public func snapshot() -> ClientConfig {
        lock.lock()
        defer { lock.unlock() }
        return config
    }

    public func update(_ next: ClientConfig) throws {
        lock.lock()
        defer { lock.unlock() }
        var normalized = next
        normalized.controlButtons = Self.normalizeButtons(next.controlButtons)
        normalized.quickTexts = Self.normalizeQuickTexts(next.quickTexts)
        normalized.commands = Self.normalizeCommands(next.commands)
        normalized.shortcutButtons = Self.normalizeShortcutButtons(next.shortcutButtons)
        normalized.updatedAt = Date()
        try Self.save(normalized, to: url)
        config = normalized
    }

    public func upsertShortcutButton(_ button: ShortcutButton) throws {
        var next = snapshot()
        if let index = next.shortcutButtons.firstIndex(where: { $0.id == button.id }) {
            next.shortcutButtons[index] = button
        } else {
            next.shortcutButtons.append(button)
        }
        try update(next)
    }

    public static func defaultConfig() -> ClientConfig {
        ClientConfig(
            controlButtons: defaultControlButtons(),
            quickTexts: defaultQuickTexts(),
            commands: defaultCommands(),
            shortcutButtons: defaultShortcutButtons(),
            updatedAt: Date()
        )
    }

    public static func defaultControlButtons() -> [ControlButton] {
        [
            ControlButton(id: "send_text", label: "Send Text", type: "sendText"),
            ControlButton(id: "backspace", label: "Backspace", type: "backspace"),
            ControlButton(id: "keyboard", label: "Keyboard", type: "keyboard"),
            ControlButton(id: "select_all", label: "Select All", type: "selectAll"),
            ControlButton(id: "enter", label: "Ent", type: "enter"),
            ControlButton(id: "cmd_enter", label: "Cmd+Ent", type: "cmdEnter"),
            ControlButton(id: "copy", label: "Copy", type: "copy"),
            ControlButton(id: "paste", label: "Paste", type: "paste"),
            ControlButton(id: "escape", label: "ESC", type: "escape"),
            ControlButton(id: "interrupt", label: "Break", type: "interrupt"),
            ControlButton(id: "undo", label: "Undo", type: "undo"),
            ControlButton(id: "close", label: "Close", type: "close")
        ]
    }

    public static func defaultQuickTexts() -> [QuickText] {
        [
            QuickText(id: "continue_fix", name: "继续修复", group: "AI Prompt", content: "继续修复上一个问题，先定位根因，再修改代码并运行相关测试。"),
            QuickText(id: "run_related_tests", name: "运行相关测试", group: "AI Prompt", content: "请运行与本次修改相关的测试，并根据失败信息继续修复。"),
            QuickText(id: "summarize_changes", name: "总结当前修改", group: "AI Prompt", content: "请总结当前修改点、验证结果，以及还存在的风险。")
        ]
    }

    public static func defaultCommands(workingDirectory: String = FileManager.default.currentDirectoryPath) -> [PresetCommand] {
        [
            PresetCommand(id: "pwd", name: "显示当前目录", command: "pwd", workingDirectory: workingDirectory, requiresConfirmation: false),
            PresetCommand(id: "git_status_short", name: "Git 状态", command: "git status --short", workingDirectory: workingDirectory, requiresConfirmation: false),
            PresetCommand(id: "ls", name: "列出文件", command: "ls", workingDirectory: workingDirectory, requiresConfirmation: false)
        ]
    }

    public static func defaultShortcutButtons() -> [ShortcutButton] {
        let screen = ScreenProvider.currentScreenInfo()
        return [
            ShortcutButton(
                id: "center_click",
                name: "点击屏幕中央",
                x: Double(screen.width) / 2.0,
                y: Double(screen.height) / 2.0,
                screenWidth: Double(screen.width),
                screenHeight: Double(screen.height),
                requiresConfirmation: false
            )
        ]
    }

    private static func load(from url: URL) -> ClientConfig {
        guard let data = try? Data(contentsOf: url),
              let decoded = try? JSONCoding.decoder.decode(ClientConfig.self, from: data) else {
            return defaultConfig()
        }
        return ClientConfig(
            controlButtons: normalizeButtons(decoded.controlButtons),
            quickTexts: normalizeQuickTexts(decoded.quickTexts),
            commands: normalizeCommands(decoded.commands),
            shortcutButtons: normalizeShortcutButtons(decoded.shortcutButtons),
            updatedAt: decoded.updatedAt
        )
    }

    private static func save(_ config: ClientConfig, to url: URL) throws {
        try FileManager.default.createDirectory(at: url.deletingLastPathComponent(), withIntermediateDirectories: true)
        try JSONCoding.encoder.encode(config).write(to: url, options: .atomic)
    }

    private static func normalizeButtons(_ buttons: [ControlButton]) -> [ControlButton] {
        let defaults = Dictionary(uniqueKeysWithValues: defaultControlButtons().map { ($0.type, $0) })
        var seen = Set<String>()
        var normalized: [ControlButton] = []
        for button in buttons {
            let type = canonicalControlType(button.type)
            guard let canonical = defaults[type], !seen.contains(type) else { continue }
            normalized.append(ControlButton(id: canonical.id, label: canonical.label, type: canonical.type))
            seen.insert(type)
        }
        for button in defaultControlButtons() where !seen.contains(button.type) {
            normalized.append(button)
        }
        return normalized
    }

    private static func canonicalControlType(_ type: String) -> String {
        type == "voice" ? "keyboard" : type
    }

    private static func normalizeQuickTexts(_ texts: [QuickText]) -> [QuickText] {
        texts.enumerated().compactMap { index, text in
            let label = text.name.trimmingCharacters(in: .whitespacesAndNewlines)
            let content = text.content.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !label.isEmpty, !content.isEmpty else { return nil }
            let id = text.id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "quick_\(index)_\(UUID().uuidString.prefix(8))" : text.id
            return QuickText(id: id, name: label, group: text.group, content: content)
        }
    }

    private static func normalizeCommands(_ commands: [PresetCommand]) -> [PresetCommand] {
        commands.enumerated().compactMap { index, command in
            let name = command.name.trimmingCharacters(in: .whitespacesAndNewlines)
            let commandText = command.command.trimmingCharacters(in: .whitespacesAndNewlines)
            let workingDirectory = command.workingDirectory.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !name.isEmpty, !commandText.isEmpty else { return nil }
            let id = command.id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "command_\(index)_\(UUID().uuidString.prefix(8))" : command.id
            return PresetCommand(
                id: id,
                name: name,
                command: commandText,
                workingDirectory: workingDirectory.isEmpty ? FileManager.default.currentDirectoryPath : workingDirectory,
                requiresConfirmation: command.requiresConfirmation
            )
        }
    }

    private static func normalizeShortcutButtons(_ buttons: [ShortcutButton]) -> [ShortcutButton] {
        buttons.enumerated().compactMap { index, button in
            let name = button.name.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !name.isEmpty, button.screenWidth > 0, button.screenHeight > 0 else { return nil }
            let id = button.id.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "shortcut_\(index)_\(UUID().uuidString.prefix(8))" : button.id
            return ShortcutButton(
                id: id,
                name: name,
                x: max(0, min(button.x, button.screenWidth)),
                y: max(0, min(button.y, button.screenHeight)),
                screenWidth: button.screenWidth,
                screenHeight: button.screenHeight,
                requiresConfirmation: button.requiresConfirmation
            )
        }
    }

    public static func defaultURL() -> URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first
            ?? URL(fileURLWithPath: FileManager.default.currentDirectoryPath)
        return base.appendingPathComponent("VibeLink", isDirectory: true).appendingPathComponent("admin-config.json")
    }
}
