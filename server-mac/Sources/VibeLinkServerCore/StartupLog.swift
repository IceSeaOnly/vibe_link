import Foundation

public enum StartupLog {
    public static func printBanner(config: ServerConfig) {
        let addresses = localIPv4Addresses()
        print("VibeLink Mac Server 0.1.0")
        print("Listening: http://0.0.0.0:\(config.port)")
        for address in addresses {
            print("LAN URL: http://\(address):\(config.port)")
        }
        print("Token: \(config.token)")
        print("Health: http://127.0.0.1:\(config.port)/health")
        print("Stream: http://127.0.0.1:\(config.port)/stream?token=\(config.token)")
        print("Admin: http://127.0.0.1:\(config.adminPort)/admin")
        if AccessibilityPermission.isTrusted(prompt: true) {
            print("Accessibility: granted")
        } else {
            print("Accessibility: not granted. Send Text, clicks, drags, and shortcuts require System Settings > Privacy & Security > Accessibility permission for the app running VibeLinkServer.")
        }
        print("Screen Recording: grant permission for screencapture if the stream is blank or fails.")
    }

    private static func localIPv4Addresses() -> [String] {
        var result: [String] = []
        var ifaddr: UnsafeMutablePointer<ifaddrs>?
        guard getifaddrs(&ifaddr) == 0, let first = ifaddr else { return result }
        defer { freeifaddrs(ifaddr) }

        var pointer: UnsafeMutablePointer<ifaddrs>? = first
        while let current = pointer {
            defer { pointer = current.pointee.ifa_next }
            let flags = Int32(current.pointee.ifa_flags)
            guard flags & IFF_UP != 0, flags & IFF_LOOPBACK == 0 else { continue }
            guard current.pointee.ifa_addr.pointee.sa_family == UInt8(AF_INET) else { continue }

            var hostname = [CChar](repeating: 0, count: Int(NI_MAXHOST))
            let address = current.pointee.ifa_addr
            let length = socklen_t(current.pointee.ifa_addr.pointee.sa_len)
            if getnameinfo(address, length, &hostname, socklen_t(hostname.count), nil, 0, NI_NUMERICHOST) == 0 {
                result.append(String(cString: hostname))
            }
        }
        return result
    }
}
