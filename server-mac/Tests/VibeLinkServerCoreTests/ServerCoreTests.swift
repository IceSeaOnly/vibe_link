import XCTest
@testable import VibeLinkServerCore

final class ServerCoreTests: XCTestCase {
    func testParsesPortAndTokenArguments() throws {
        let config = try ServerConfig(arguments: ["VibeLinkServer", "--port", "9001", "--token", "abc"])

        XCTAssertEqual(config.port, 9001)
        XCTAssertEqual(config.adminPort, 9002)
        XCTAssertEqual(config.token, "abc")
    }

    func testParsesAdminPortArgument() throws {
        let config = try ServerConfig(arguments: ["VibeLinkServer", "--port", "9001", "--admin-port", "9101", "--token", "abc"])

        XCTAssertEqual(config.port, 9001)
        XCTAssertEqual(config.adminPort, 9101)
        XCTAssertEqual(config.token, "abc")
    }

    func testRejectsInvalidBearerToken() {
        let config = ServerConfig(port: 8765, token: "secret")
        let request = HTTPRequest(method: "GET", path: "/api/commands", query: [:], headers: ["authorization": "Bearer wrong"], body: Data())

        XCTAssertFalse(Auth.isAuthorized(request: request, config: config))
    }

    func testBuiltInCommandsArePresetOnly() {
        let registry = PresetRegistry(workingDirectory: "/tmp")

        XCTAssertNotNil(registry.command(id: "pwd"))
        XCTAssertNil(registry.command(id: "rm -rf /"))
        XCTAssertGreaterThanOrEqual(registry.commands.count, 3)
    }

    func testAccessibilityErrorExplainsClipboardFallback() {
        let message = InputError.accessibilityNotTrusted.localizedDescription

        XCTAssertTrue(message.contains("clipboard"))
        XCTAssertTrue(message.contains("Accessibility"))
    }

    func testDisplaysAreAvailableWithoutDependingOnCount() {
        let displays = ScreenProvider.displays()

        XCTAssertFalse(displays.isEmpty)
        XCTAssertTrue(displays.contains(where: { $0.width > 0 && $0.height > 0 }))
        XCTAssertTrue(displays.contains(where: \.isMain))
    }

    func testDisplayLookupFallsBackToMainDisplay() {
        let fallback = ScreenProvider.display(id: "not-a-real-display-id")
        let main = ScreenProvider.displays().first(where: \.isMain)

        XCTAssertEqual(fallback.id, main?.id)
    }

    func testHealthResponseEncodingIncludesDisplays() throws {
        let display = DisplayInfo(id: "1", name: "Built-in Display", x: 0, y: 0, width: 1512, height: 982, scale: 2, isMain: true)
        let response = HealthResponse(
            ok: true,
            name: "VibeLink Mac Server",
            version: "0.3.0",
            streamUrl: "/stream",
            lowLatencyStreamUrl: "/stream-ws",
            videoStreamUrl: "/stream-h264",
            screen: ScreenInfo(width: 1512, height: 982, scale: 2),
            displays: [display]
        )

        let data = try JSONCoding.encoder.encode(response)
        let json = try XCTUnwrap(String(data: data, encoding: .utf8))
        XCTAssertTrue(json.contains("\"displays\""))
        XCTAssertTrue(json.contains("\"lowLatencyStreamUrl\""))
        XCTAssertTrue(json.contains("\"videoStreamUrl\""))
        XCTAssertTrue(json.contains("\"Built-in Display\""))
    }

    func testH264AnnexBWrapsNalUnitsWithStartCodes() {
        let payload = H264AnnexB.wrap(nalUnits: [Data([0x67, 0x01]), Data([0x68, 0x02])])

        XCTAssertEqual(payload, Data([0, 0, 0, 1, 0x67, 0x01, 0, 0, 0, 1, 0x68, 0x02]))
    }

    func testH264LowLatencySettingsPreferShortQueueOverHighFrameRate() {
        let settings = H264StreamSettings.lowLatency

        XCTAssertEqual(settings.fps, 20)
        XCTAssertEqual(settings.bitRate, 3_000_000)
        XCTAssertEqual(settings.keyFrameInterval, 20)
        XCTAssertEqual(settings.frameIntervalMilliseconds, 50)
    }

    func testPairingInfoIncludesLanUrlsAndToken() {
        let config = ServerConfig(port: 8765, adminPort: 8766, token: "secret")
        let info = PairingProvider.info(config: config)

        XCTAssertEqual(info.token, "secret")
        XCTAssertTrue(info.pairingUri.contains("vibelink://pair"))
        XCTAssertTrue(info.pairingUri.contains("token=secret"))
        XCTAssertFalse(info.serverUrls.isEmpty)
        XCTAssertTrue(info.adminUrls.allSatisfy { $0.hasSuffix("/admin") })
    }

    func testPermissionsRouteRequiresAuth() async throws {
        let store = AdminConfigStore(url: FileManager.default.temporaryDirectory.appendingPathComponent("vibelink-\(UUID().uuidString).json"))
        let config = ServerConfig(port: 8765, token: "secret")
        let router = Router(config: config, registry: PresetRegistry(workingDirectory: "/tmp"), commandRunner: CommandRunner(), store: store)

        let unauthorized = await router.route(HTTPRequest(method: "GET", path: "/api/permissions", query: [:], headers: [:], body: Data()))
        let authorized = await router.route(HTTPRequest(method: "GET", path: "/api/permissions", query: [:], headers: ["authorization": "Bearer secret"], body: Data()))

        XCTAssertEqual(unauthorized.status, 401)
        XCTAssertEqual(authorized.status, 200)
        XCTAssertNoThrow(try JSONCoding.decoder.decode(PermissionsResponse.self, from: authorized.body))
    }

    func testCaptureSourcesRouteReturnsDisplaysAndWindows() async throws {
        let store = AdminConfigStore(url: FileManager.default.temporaryDirectory.appendingPathComponent("vibelink-\(UUID().uuidString).json"))
        let manager = CaptureSourceManager(provider: StaticCaptureSourceProvider(sources: [
            CaptureSource(id: "display:1", type: .display, name: "Built-in Display", appName: nil, x: 0, y: 0, width: 1512, height: 982, scale: 2, isMain: true),
            CaptureSource(id: "window:42", type: .window, name: "Terminal - zsh", appName: "Terminal", x: 100, y: 120, width: 900, height: 600, scale: 1, isMain: false)
        ]))
        let router = Router(config: ServerConfig(port: 8765, token: "secret"), registry: PresetRegistry(workingDirectory: "/tmp"), commandRunner: CommandRunner(), store: store, captureSources: manager)

        let response = await router.route(HTTPRequest(method: "GET", path: "/api/capture-sources", query: [:], headers: ["authorization": "Bearer secret"], body: Data()))
        let decoded = try JSONCoding.decoder.decode(CaptureSourcesResponse.self, from: response.body)

        XCTAssertEqual(response.status, 200)
        XCTAssertEqual(decoded.sources.map(\.id), ["display:1", "window:42"])
        XCTAssertEqual(decoded.selected?.id, "display:1")
        XCTAssertEqual(decoded.sources.last?.type, .window)
    }

    func testCaptureSourceSelectionPersistsInManager() async throws {
        let store = AdminConfigStore(url: FileManager.default.temporaryDirectory.appendingPathComponent("vibelink-\(UUID().uuidString).json"))
        let activator = RecordingCaptureSourceActivator()
        let manager = CaptureSourceManager(provider: StaticCaptureSourceProvider(sources: [
            CaptureSource(id: "display:1", type: .display, name: "Built-in Display", appName: nil, x: 0, y: 0, width: 1512, height: 982, scale: 2, isMain: true),
            CaptureSource(id: "window:42", type: .window, name: "Terminal - zsh", appName: "Terminal", x: 100, y: 120, width: 900, height: 600, scale: 1, isMain: false)
        ]), activator: activator)
        let router = Router(config: ServerConfig(port: 8765, token: "secret"), registry: PresetRegistry(workingDirectory: "/tmp"), commandRunner: CommandRunner(), store: store, captureSources: manager)
        let requestBody = try JSONCoding.encoder.encode(CaptureSourceSelectionRequest(id: "window:42"))

        let response = await router.route(HTTPRequest(method: "POST", path: "/api/capture-source", query: [:], headers: ["authorization": "Bearer secret"], body: requestBody))
        let decoded = try JSONCoding.decoder.decode(CaptureSourcesResponse.self, from: response.body)

        XCTAssertEqual(response.status, 200)
        XCTAssertEqual(decoded.selected?.id, "window:42")
        XCTAssertEqual(manager.selectedSource()?.id, "window:42")
        XCTAssertEqual(activator.activatedSourceIds, ["window:42"])
    }

    func testWindowFocusControllerFindsOwnerProcessForWindowID() {
        let info: [[String: Any]] = [
            [
                kCGWindowNumber as String: UInt32(41),
                kCGWindowOwnerPID as String: pid_t(111)
            ],
            [
                kCGWindowNumber as String: UInt32(42),
                kCGWindowOwnerPID as String: pid_t(222)
            ]
        ]

        let pid = WindowFocusController.ownerProcessIdentifier(windowID: 42, from: info)

        XCTAssertEqual(pid, 222)
    }

    func testDisplayPointMappingUsesGlobalDisplayOrigin() {
        let display = DisplayInfo(id: "2", name: "External", x: 1512, y: -100, width: 1000, height: 500, scale: 1, isMain: false)

        let point = InputController.mapPoint(x: 50, y: 25, sourceWidth: 100, sourceHeight: 50, display: display)

        XCTAssertEqual(point.x, 2012, accuracy: 0.001)
        XCTAssertEqual(point.y, 150, accuracy: 0.001)
    }

    func testWindowCaptureSourcePointMappingUsesWindowOrigin() {
        let source = CaptureSource(id: "window:42", type: .window, name: "Terminal", appName: "Terminal", x: 100, y: 120, width: 900, height: 600, scale: 1, isMain: false)

        let point = InputController.mapPoint(x: 450, y: 300, sourceWidth: 900, sourceHeight: 600, captureSource: source)

        XCTAssertEqual(point.x, 550, accuracy: 0.001)
        XCTAssertEqual(point.y, 420, accuracy: 0.001)
    }

    func testWindowSourceInputFocusesForAbsoluteClickButNotTrackpadMove() {
        XCTAssertTrue(InputController.shouldFocusWindowSource(for: "tap"))
        XCTAssertTrue(InputController.shouldFocusWindowSource(for: "drag"))
        XCTAssertTrue(InputController.shouldFocusWindowSource(for: "mouseDown"))
        XCTAssertTrue(InputController.shouldFocusWindowSource(for: "mouseUp"))
        XCTAssertTrue(InputController.shouldFocusWindowSource(for: "text"))
        XCTAssertFalse(InputController.shouldFocusWindowSource(for: "relativeMove"))
    }

    func testWindowCaptureCacheReusesStreamPerWindowID() {
        let cache = WindowStreamCache<Int>()
        var factoryCalls = 0

        let first = try? cache.value(for: 42) {
            factoryCalls += 1
            return factoryCalls
        }
        let second = try? cache.value(for: 42) {
            factoryCalls += 1
            return factoryCalls
        }
        let third = try? cache.value(for: 43) {
            factoryCalls += 1
            return factoryCalls
        }

        XCTAssertEqual(first, 1)
        XCTAssertEqual(second, 1)
        XCTAssertEqual(third, 2)
        XCTAssertEqual(cache.count, 2)
        XCTAssertEqual(factoryCalls, 2)
    }

    func testAdminConfigStorePersistsButtonAndQuickTextOrder() throws {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("vibelink-\(UUID().uuidString)")
            .appendingPathComponent("admin-config.json")
        let store = AdminConfigStore(url: url)
        let next = ClientConfig(
            controlButtons: [
                ControlButton(id: "paste", label: "Ignored", type: "paste"),
                ControlButton(id: "copy", label: "Ignored", type: "copy")
            ],
            quickTexts: [
                QuickText(id: "second", name: "Second", group: "", content: "two"),
                QuickText(id: "first", name: "First", group: "", content: "one")
            ],
            commands: [
                PresetCommand(id: "custom", name: "Custom", command: "echo custom", workingDirectory: "/tmp", requiresConfirmation: false)
            ],
            shortcutButtons: [
                ShortcutButton(id: "custom_click", name: "Custom Click", x: 10, y: 20, screenWidth: 100, screenHeight: 80, requiresConfirmation: false)
            ],
            updatedAt: Date(timeIntervalSince1970: 0)
        )

        try store.update(next)
        let reloaded = AdminConfigStore(url: url).snapshot()

        XCTAssertEqual(reloaded.controlButtons.prefix(2).map(\.type), ["paste", "copy"])
        XCTAssertEqual(reloaded.controlButtons.count, 12)
        XCTAssertEqual(reloaded.controlButtons[0].label, "Paste")
        XCTAssertEqual(reloaded.quickTexts.map(\.id), ["second", "first"])
        XCTAssertEqual(reloaded.quickTexts.map(\.content), ["two", "one"])
        XCTAssertEqual(reloaded.commands.map(\.id), ["custom"])
        XCTAssertEqual(reloaded.commands.first?.command, "echo custom")
        XCTAssertEqual(reloaded.shortcutButtons.map(\.id), ["custom_click"])
        XCTAssertEqual(reloaded.shortcutButtons.first?.x, 10)
    }

    func testInterruptControlButtonUsesBreakLabel() {
        let interrupt = AdminConfigStore.defaultControlButtons().first { $0.type == "interrupt" }

        XCTAssertEqual(interrupt?.label, "Break")
    }

    func testDefaultControlButtonsUseKeyboardInsteadOfVoice() {
        let keyboard = AdminConfigStore.defaultControlButtons().first { $0.type == "keyboard" }
        let voice = AdminConfigStore.defaultControlButtons().first { $0.type == "voice" }

        XCTAssertEqual(keyboard?.label, "Keyboard")
        XCTAssertNil(voice)
    }

    func testAdminConfigStoreMigratesLegacyVoiceButtonToKeyboard() throws {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("vibelink-\(UUID().uuidString)")
            .appendingPathComponent("admin-config.json")
        let store = AdminConfigStore(url: url)
        let next = ClientConfig(
            controlButtons: [
                ControlButton(id: "send_text", label: "Ignored", type: "sendText"),
                ControlButton(id: "voice", label: "Voice", type: "voice"),
                ControlButton(id: "paste", label: "Ignored", type: "paste")
            ],
            quickTexts: [],
            commands: [],
            shortcutButtons: [],
            updatedAt: Date(timeIntervalSince1970: 0)
        )

        try store.update(next)
        let reloaded = AdminConfigStore(url: url).snapshot()

        XCTAssertEqual(reloaded.controlButtons.prefix(3).map(\.type), ["sendText", "keyboard", "paste"])
        XCTAssertEqual(reloaded.controlButtons[1].label, "Keyboard")
    }

    func testAdminConfigStoreDefaultCommandsAreEditablePresets() {
        let commands = AdminConfigStore.defaultCommands()

        XCTAssertEqual(commands.map(\.id), ["pwd", "git_status_short", "ls"])
        XCTAssertEqual(commands.first?.workingDirectory.isEmpty, false)
    }

    func testClientConfigRouteReturnsAdminStoreSnapshot() async throws {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("vibelink-\(UUID().uuidString)")
            .appendingPathComponent("admin-config.json")
        let store = AdminConfigStore(url: url)
        let config = ServerConfig(port: 8765, token: "secret")
        let router = Router(config: config, registry: PresetRegistry(workingDirectory: "/tmp"), commandRunner: CommandRunner(), store: store)
        let next = ClientConfig(
            controlButtons: [
                ControlButton(id: "undo", label: "Ignored", type: "undo"),
                ControlButton(id: "send_text", label: "Ignored", type: "sendText")
            ],
            quickTexts: [
                QuickText(id: "reply", name: "Reply", group: "", content: "content")
            ],
            commands: [
                PresetCommand(id: "custom_command", name: "Custom Command", command: "echo managed", workingDirectory: "/tmp", requiresConfirmation: false)
            ],
            shortcutButtons: [
                ShortcutButton(id: "custom_click", name: "Custom Click", x: 10, y: 20, screenWidth: 100, screenHeight: 80, requiresConfirmation: false)
            ],
            updatedAt: Date(timeIntervalSince1970: 0)
        )
        try store.update(next)

        let response = await router.route(HTTPRequest(
            method: "GET",
            path: "/api/client-config",
            query: [:],
            headers: ["authorization": "Bearer secret"],
            body: Data()
        ))
        let decoded = try JSONCoding.decoder.decode(ClientConfig.self, from: response.body)

        XCTAssertEqual(response.status, 200)
        XCTAssertEqual(decoded.controlButtons.prefix(2).map(\.type), ["undo", "sendText"])
        XCTAssertEqual(decoded.quickTexts.map(\.name), ["Reply"])
        XCTAssertEqual(decoded.commands.map(\.name), ["Custom Command"])
        XCTAssertEqual(decoded.shortcutButtons.map(\.name), ["Custom Click"])
    }

    func testCommandsRouteReturnsAdminConfiguredCommands() async throws {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("vibelink-\(UUID().uuidString)")
            .appendingPathComponent("admin-config.json")
        let store = AdminConfigStore(url: url)
        let config = ServerConfig(port: 8765, token: "secret")
        let router = Router(config: config, registry: PresetRegistry(workingDirectory: "/tmp"), commandRunner: CommandRunner(), store: store)
        try store.update(ClientConfig(
            controlButtons: AdminConfigStore.defaultControlButtons(),
            quickTexts: [],
            commands: [
                PresetCommand(id: "custom_command", name: "Custom Command", command: "echo managed", workingDirectory: "/tmp", requiresConfirmation: false)
            ],
            shortcutButtons: [],
            updatedAt: Date()
        ))

        let response = await router.route(HTTPRequest(
            method: "GET",
            path: "/api/commands",
            query: [:],
            headers: ["authorization": "Bearer secret"],
            body: Data()
        ))
        let decoded = try JSONCoding.decoder.decode([PresetCommand].self, from: response.body)

        XCTAssertEqual(response.status, 200)
        XCTAssertEqual(decoded.map(\.id), ["custom_command"])
        XCTAssertEqual(decoded.first?.command, "echo managed")
    }

    func testShortcutButtonsRouteReturnsAdminConfiguredButtons() async throws {
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("vibelink-\(UUID().uuidString)")
            .appendingPathComponent("admin-config.json")
        let store = AdminConfigStore(url: url)
        let config = ServerConfig(port: 8765, token: "secret")
        let router = Router(config: config, registry: PresetRegistry(workingDirectory: "/tmp"), commandRunner: CommandRunner(), store: store)
        try store.update(ClientConfig(
            controlButtons: AdminConfigStore.defaultControlButtons(),
            quickTexts: [],
            commands: [],
            shortcutButtons: [
                ShortcutButton(id: "custom_click", name: "Custom Click", x: 10, y: 20, screenWidth: 100, screenHeight: 80, requiresConfirmation: false)
            ],
            updatedAt: Date()
        ))

        let response = await router.route(HTTPRequest(
            method: "GET",
            path: "/api/shortcut-buttons",
            query: [:],
            headers: ["authorization": "Bearer secret"],
            body: Data()
        ))
        let decoded = try JSONCoding.decoder.decode([ShortcutButton].self, from: response.body)

        XCTAssertEqual(response.status, 200)
        XCTAssertEqual(decoded.map(\.id), ["custom_click"])
        XCTAssertEqual(decoded.first?.y, 20)
    }
}

private final class RecordingCaptureSourceActivator: CaptureSourceActivating, @unchecked Sendable {
    private let lock = NSLock()
    private var values: [String] = []

    var activatedSourceIds: [String] {
        lock.lock()
        defer { lock.unlock() }
        return values
    }

    func activate(source: CaptureSource?) {
        guard let source else { return }
        lock.lock()
        values.append(source.id)
        lock.unlock()
    }
}
