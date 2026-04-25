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
            version: "0.2.0",
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

    func testDisplayPointMappingUsesGlobalDisplayOrigin() {
        let display = DisplayInfo(id: "2", name: "External", x: 1512, y: -100, width: 1000, height: 500, scale: 1, isMain: false)

        let point = InputController.mapPoint(x: 50, y: 25, sourceWidth: 100, sourceHeight: 50, display: display)

        XCTAssertEqual(point.x, 2012, accuracy: 0.001)
        XCTAssertEqual(point.y, 150, accuracy: 0.001)
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
