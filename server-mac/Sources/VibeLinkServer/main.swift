import Foundation
import VibeLinkServerCore

func printUsage() {
    print("""
    Usage: swift run VibeLinkServer [--port 8765] [--admin-port 8766] [--token token]
    """)
}

do {
    let config = try ServerConfig(arguments: CommandLine.arguments)
    let registry = PresetRegistry()
    let store = AdminConfigStore()
    let runner = CommandRunner()
    let router = Router(config: config, registry: registry, commandRunner: runner, store: store)
    let adminRouter = AdminRouter(config: config, store: store)
    let server = HTTPServer(config: config, router: router)
    let adminServer = HTTPServer(config: ServerConfig(port: config.adminPort, adminPort: config.adminPort, token: config.token), router: adminRouter, publishesBonjour: false)

    try server.start()
    try adminServer.start()
    StartupLog.printBanner(config: config)
    dispatchMain()
} catch ConfigError.helpRequested {
    printUsage()
} catch {
    print(error.localizedDescription)
    printUsage()
    exit(1)
}
