import Foundation

public actor CommandRunner {
    private var runs: [String: CommandRunRecord] = [:]

    public init() {}

    public func start(command: PresetCommand) -> CommandRunRecord {
        let runId = "run_\(UUID().uuidString.replacingOccurrences(of: "-", with: "").prefix(12))"
        let record = CommandRunRecord(
            id: runId,
            commandId: command.id,
            status: .running,
            exitCode: nil,
            output: "",
            startedAt: Date(),
            finishedAt: nil
        )
        runs[runId] = record

        Task.detached { [weak self] in
            let result = Self.execute(command)
            await self?.finish(runId: runId, exitCode: result.exitCode, output: result.output)
        }

        return record
    }

    public func get(runId: String) -> CommandRunRecord? {
        runs[runId]
    }

    private func finish(runId: String, exitCode: Int32, output: String) {
        guard var record = runs[runId] else { return }
        record.exitCode = exitCode
        record.output = output
        record.status = exitCode == 0 ? .succeeded : .failed
        record.finishedAt = Date()
        runs[runId] = record
        print("Command \(record.commandId) finished with exit code \(exitCode)")
    }

    private static func execute(_ command: PresetCommand) -> (exitCode: Int32, output: String) {
        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/bin/zsh")
        process.arguments = ["-lc", command.command]
        process.currentDirectoryURL = URL(fileURLWithPath: command.workingDirectory)

        let output = Pipe()
        process.standardOutput = output
        process.standardError = output

        do {
            try process.run()
            process.waitUntilExit()
            let data = output.fileHandleForReading.readDataToEndOfFile()
            let text = String(data: data, encoding: .utf8) ?? ""
            return (process.terminationStatus, text)
        } catch {
            return (127, "Failed to run command: \(error.localizedDescription)\n")
        }
    }
}
