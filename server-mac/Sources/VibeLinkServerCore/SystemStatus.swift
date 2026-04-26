import AppKit
import ApplicationServices
import CoreImage
import Foundation

public enum NetworkInfo {
    public static func localIPv4Addresses() -> [String] {
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

    public static func serverUrls(port: Int) -> [String] {
        let lan = localIPv4Addresses().map { "http://\($0):\(port)" }
        return lan.isEmpty ? ["http://127.0.0.1:\(port)"] : lan
    }
}

public enum PermissionChecker {
    public static func response() -> PermissionsResponse {
        PermissionsResponse(ok: true, permissions: [
            PermissionStatus(
                id: "screenRecording",
                name: "Screen Recording",
                granted: screenRecordingGranted(),
                required: true,
                guidance: "System Settings > Privacy & Security > Screen Recording. Grant permission to the terminal or app running VibeLinkServer, then restart the server."
            ),
            PermissionStatus(
                id: "accessibility",
                name: "Accessibility",
                granted: AccessibilityPermission.isTrusted(prompt: false),
                required: true,
                guidance: "System Settings > Privacy & Security > Accessibility. Grant permission to the terminal or app running VibeLinkServer for clicks, drags, keyboard shortcuts, and text paste."
            )
        ])
    }

    public static func screenRecordingGranted() -> Bool {
        if #available(macOS 10.15, *) {
            return CGPreflightScreenCaptureAccess()
        }
        return true
    }
}

public enum PairingProvider {
    public static func info(config: ServerConfig) -> PairingInfo {
        let serverUrls = NetworkInfo.serverUrls(port: Int(config.port))
        let adminUrls = NetworkInfo.serverUrls(port: Int(config.adminPort)).map { "\($0)/admin" }
        let preferredUrl = serverUrls.first ?? "http://127.0.0.1:\(config.port)"
        let uri = "vibelink://pair?url=\(urlEncode(preferredUrl))&token=\(urlEncode(config.token))"
        return PairingInfo(
            appName: "VibeLink",
            version: "0.3.0",
            serverUrls: serverUrls,
            adminUrls: adminUrls,
            token: config.token,
            pairingUri: uri
        )
    }

    public static func qrPNG(config: ServerConfig, size: CGFloat = 512) throws -> Data {
        let payload = info(config: config).pairingUri
        let filter = CIFilter(name: "CIQRCodeGenerator")
        filter?.setValue(Data(payload.utf8), forKey: "inputMessage")
        filter?.setValue("M", forKey: "inputCorrectionLevel")
        guard let image = filter?.outputImage else {
            throw PairingError.qrGenerationFailed
        }
        let scale = size / image.extent.width
        let scaled = image.transformed(by: CGAffineTransform(scaleX: scale, y: scale))
        let rep = NSCIImageRep(ciImage: scaled)
        let bitmap = NSBitmapImageRep(bitmapDataPlanes: nil, pixelsWide: Int(size), pixelsHigh: Int(size), bitsPerSample: 8, samplesPerPixel: 4, hasAlpha: true, isPlanar: false, colorSpaceName: .deviceRGB, bytesPerRow: 0, bitsPerPixel: 0)
        guard let bitmap else { throw PairingError.qrGenerationFailed }
        NSGraphicsContext.saveGraphicsState()
        NSGraphicsContext.current = NSGraphicsContext(bitmapImageRep: bitmap)
        NSColor.white.setFill()
        NSRect(x: 0, y: 0, width: size, height: size).fill()
        rep.draw(in: NSRect(x: 0, y: 0, width: size, height: size))
        NSGraphicsContext.restoreGraphicsState()
        guard let png = bitmap.representation(using: .png, properties: [:]) else {
            throw PairingError.qrGenerationFailed
        }
        return png
    }

    private static func urlEncode(_ value: String) -> String {
        value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? value
    }
}

public enum PairingError: LocalizedError {
    case qrGenerationFailed

    public var errorDescription: String? {
        "Failed to generate pairing QR code"
    }
}
