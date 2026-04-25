import Foundation

public enum StartupLog {
    public static func printBanner(config: ServerConfig) {
        let addresses = NetworkInfo.localIPv4Addresses()
        print("VibeLink Mac Server 0.2.0")
        print("Listening: http://0.0.0.0:\(config.port)")
        for address in addresses {
            print("LAN URL: http://\(address):\(config.port)")
        }
        print("Token: \(config.token)")
        print("Health: http://127.0.0.1:\(config.port)/health")
        print("Stream: http://127.0.0.1:\(config.port)/stream?token=\(config.token)")
        print("Low latency stream: ws://127.0.0.1:\(config.port)/stream-ws?token=\(config.token)")
        print("H.264 stream: ws://127.0.0.1:\(config.port)/stream-h264?token=\(config.token)")
        print("Pairing URI: \(PairingProvider.info(config: config).pairingUri)")
        print("Admin: http://127.0.0.1:\(config.adminPort)/admin")
        if AccessibilityPermission.isTrusted(prompt: true) {
            print("Accessibility: granted")
        } else {
            print("Accessibility: not granted. Send Text, clicks, drags, and shortcuts require System Settings > Privacy & Security > Accessibility permission for the app running VibeLinkServer.")
        }
        let screenStatus = PermissionChecker.screenRecordingGranted() ? "granted" : "not granted"
        print("Screen Recording: \(screenStatus)")
    }
}
