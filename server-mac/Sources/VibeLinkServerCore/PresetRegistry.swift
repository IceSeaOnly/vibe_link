import Foundation

public final class PresetRegistry: @unchecked Sendable {
    public let quickTexts: [QuickText]
    public let commands: [PresetCommand]
    public let shortcutButtons: [ShortcutButton]

    public init(workingDirectory: String = FileManager.default.currentDirectoryPath) {
        quickTexts = [
            QuickText(id: "continue_fix", name: "继续修复", group: "AI Prompt", content: "继续修复上一个问题，先定位根因，再修改代码并运行相关测试。"),
            QuickText(id: "run_related_tests", name: "运行相关测试", group: "AI Prompt", content: "请运行与本次修改相关的测试，并根据失败信息继续修复。"),
            QuickText(id: "summarize_changes", name: "总结当前修改", group: "AI Prompt", content: "请总结当前修改点、验证结果，以及还存在的风险。")
        ]

        commands = [
            PresetCommand(id: "pwd", name: "显示当前目录", command: "pwd", workingDirectory: workingDirectory, requiresConfirmation: false),
            PresetCommand(id: "git_status_short", name: "Git 状态", command: "git status --short", workingDirectory: workingDirectory, requiresConfirmation: false),
            PresetCommand(id: "ls", name: "列出文件", command: "ls", workingDirectory: workingDirectory, requiresConfirmation: false)
        ]

        let screen = ScreenProvider.currentScreenInfo()
        shortcutButtons = [
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

    public func command(id: String) -> PresetCommand? {
        commands.first { $0.id == id }
    }

    public func shortcutButton(id: String) -> ShortcutButton? {
        shortcutButtons.first { $0.id == id }
    }
}
