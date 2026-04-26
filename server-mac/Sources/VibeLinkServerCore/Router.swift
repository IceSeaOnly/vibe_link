import Foundation

public final class Router: HTTPRouting, @unchecked Sendable {
    private let config: ServerConfig
    private let registry: PresetRegistry
    private let commandRunner: CommandRunner
    private let store: AdminConfigStore
    private let captureSources: CaptureSourceManager

    public init(config: ServerConfig, registry: PresetRegistry, commandRunner: CommandRunner, store: AdminConfigStore, captureSources: CaptureSourceManager = CaptureSourceManager()) {
        self.config = config
        self.registry = registry
        self.commandRunner = commandRunner
        self.store = store
        self.captureSources = captureSources
    }

    public func route(_ request: HTTPRequest) async -> HTTPResponse {
        if request.method == "GET", request.path == "/health" {
            return HTTPResponse.json(HealthResponse(ok: true, name: "VibeLink Mac Server", version: "0.3.0", streamUrl: "/stream", lowLatencyStreamUrl: "/stream-ws", videoStreamUrl: "/stream-h264", screen: ScreenProvider.currentScreenInfo(), displays: ScreenProvider.displays()))
        }

        guard Auth.isAuthorized(request: request, config: config) else {
            return HTTPResponse.json(ErrorResponse(ok: false, error: "Unauthorized"), status: 401, reason: "Unauthorized")
        }

        do {
            switch (request.method, request.path) {
            case ("GET", "/api/displays"):
                return HTTPResponse.json(ScreenProvider.displays())

            case ("GET", "/api/permissions"):
                return HTTPResponse.json(PermissionChecker.response())

            case ("GET", "/api/pairing-info"):
                return HTTPResponse.json(PairingProvider.info(config: config))

            case ("GET", "/api/capture-sources"):
                return HTTPResponse.json(captureSources.response())

            case ("POST", "/api/capture-source"):
                let selection = try JSONCoding.decoder.decode(CaptureSourceSelectionRequest.self, from: request.body)
                return HTTPResponse.json(try captureSources.select(id: selection.id))

            case ("GET", "/api/client-config"):
                return HTTPResponse.json(store.snapshot())

            case ("POST", "/api/control"):
                let control = try JSONCoding.decoder.decode(ControlRequest.self, from: request.body)
                try InputController.perform(control, captureSource: captureSources.selectedSource())
                return HTTPResponse.json(OKResponse(ok: true))

            case ("GET", "/api/quick-texts"):
                return HTTPResponse.json(store.snapshot().quickTexts)

            case ("GET", "/api/control-buttons"):
                return HTTPResponse.json(store.snapshot().controlButtons)

            case ("GET", "/api/commands"):
                return HTTPResponse.json(store.snapshot().commands)

            case ("POST", "/api/commands/run"):
                let run = try JSONCoding.decoder.decode(RunCommandRequest.self, from: request.body)
                guard let command = store.snapshot().commands.first(where: { $0.id == run.id }) else {
                    return HTTPResponse.json(ErrorResponse(ok: false, error: "Unknown command id"), status: 404, reason: "Not Found")
                }
                let record = await commandRunner.start(command: command)
                return HTTPResponse.json(RunCommandResponse(ok: true, runId: record.id, status: record.status))

            case ("GET", _ ) where request.path.hasPrefix("/api/commands/runs/"):
                let runId = String(request.path.dropFirst("/api/commands/runs/".count))
                guard let record = await commandRunner.get(runId: runId) else {
                    return HTTPResponse.json(ErrorResponse(ok: false, error: "Unknown run id"), status: 404, reason: "Not Found")
                }
                return HTTPResponse.json(record)

            case ("GET", "/api/shortcut-buttons"):
                return HTTPResponse.json(store.snapshot().shortcutButtons)

            case ("POST", "/api/shortcut-buttons"):
                let button = try JSONCoding.decoder.decode(ShortcutButton.self, from: request.body)
                try store.upsertShortcutButton(button)
                return HTTPResponse.json(store.snapshot().shortcutButtons)

            case ("POST", "/api/shortcut-buttons/run"):
                let run = try JSONCoding.decoder.decode(RunShortcutButtonRequest.self, from: request.body)
                guard let button = store.snapshot().shortcutButtons.first(where: { $0.id == run.id }) else {
                    return HTTPResponse.json(ErrorResponse(ok: false, error: "Unknown shortcut button id"), status: 404, reason: "Not Found")
                }
                try InputController.runShortcutButton(button)
                return HTTPResponse.json(OKResponse(ok: true))

            default:
                return HTTPResponse.json(ErrorResponse(ok: false, error: "Not found"), status: 404, reason: "Not Found")
            }
        } catch {
            print("Request failed: \(request.method) \(request.path): \(error)")
            return HTTPResponse.json(ErrorResponse(ok: false, error: error.localizedDescription), status: 400, reason: "Bad Request")
        }
    }
}
