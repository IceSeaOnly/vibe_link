import AppKit
import CoreGraphics
import Foundation
import ImageIO

public enum ScreenProvider {
    public static func currentScreenInfo() -> ScreenInfo {
        let display = display(id: nil)
        return ScreenInfo(width: display.width, height: display.height, scale: display.scale)
    }

    public static func displays() -> [DisplayInfo] {
        activeDisplayIDs().enumerated().map { index, displayID in
            displayInfo(displayID: displayID, index: index)
        }
    }

    public static func display(id: String?) -> DisplayInfo {
        let allDisplays = displays()
        if let id, let display = allDisplays.first(where: { $0.id == id }) {
            return display
        }
        if let main = allDisplays.first(where: { $0.isMain }) {
            return main
        }
        if let first = allDisplays.first {
            return first
        }
        return displayInfo(displayID: CGMainDisplayID(), index: 0)
    }

    static func displayNumber(id: String?) -> Int? {
        guard let id else { return nil }
        let displayIDs = activeDisplayIDs()
        if let index = displayIDs.firstIndex(where: { String($0) == id }) {
            return index + 1
        }
        return displayIDs.firstIndex(of: CGMainDisplayID()).map { $0 + 1 }
    }

    static func directDisplayID(id: String?) -> CGDirectDisplayID {
        guard let id else { return CGMainDisplayID() }
        let displayIDs = activeDisplayIDs()
        return displayIDs.first(where: { String($0) == id }) ?? CGMainDisplayID()
    }

    private static func activeDisplayIDs() -> [CGDirectDisplayID] {
        var count: UInt32 = 0
        let countResult = CGGetActiveDisplayList(0, nil, &count)
        guard countResult == .success, count > 0 else {
            return [CGMainDisplayID()]
        }

        var displays = Array(repeating: CGDirectDisplayID(), count: Int(count))
        let listResult = CGGetActiveDisplayList(count, &displays, &count)
        guard listResult == .success, count > 0 else {
            return [CGMainDisplayID()]
        }
        return Array(displays.prefix(Int(count)))
    }

    private static func displayInfo(displayID: CGDirectDisplayID, index: Int) -> DisplayInfo {
        let bounds = CGDisplayBounds(displayID)
        let pixelWidth = CGDisplayPixelsWide(displayID)
        let scale = matchingScreen(displayID: displayID)?.backingScaleFactor
            ?? (bounds.width > 0 ? Double(pixelWidth) / Double(bounds.width) : 1.0)
        return DisplayInfo(
            id: String(displayID),
            name: displayName(displayID: displayID) ?? "Display \(index + 1)",
            x: Int(bounds.origin.x),
            y: Int(bounds.origin.y),
            width: Int(bounds.width),
            height: Int(bounds.height),
            scale: scale,
            isMain: displayID == CGMainDisplayID()
        )
    }

    private static func displayName(displayID: CGDirectDisplayID) -> String? {
        matchingScreen(displayID: displayID)?.localizedName
    }

    private static func matchingScreen(displayID: CGDirectDisplayID) -> NSScreen? {
        NSScreen.screens.first { screen in
            guard let number = screen.deviceDescription[NSDeviceDescriptionKey("NSScreenNumber")] as? NSNumber else {
                return false
            }
            return number.uint32Value == displayID
        }
    }
}

public enum ScreenCapture {
    public static func captureImage(displayId: String? = nil) throws -> CGImage {
        let displayID = ScreenProvider.directDisplayID(id: displayId)
        guard let image = CGDisplayCreateImage(displayID) else {
            throw CaptureError.failed("CGDisplayCreateImage failed")
        }
        return image
    }

    public static func captureJPEG(displayId: String? = nil) throws -> Data {
        let temporaryURL = URL(fileURLWithPath: NSTemporaryDirectory())
            .appendingPathComponent("vibelink-\(UUID().uuidString).jpg")
        defer { try? FileManager.default.removeItem(at: temporaryURL) }

        let process = Process()
        process.executableURL = URL(fileURLWithPath: "/usr/sbin/screencapture")
        var arguments = ["-x", "-t", "jpg"]
        if let displayNumber = ScreenProvider.displayNumber(id: displayId) {
            arguments.append(contentsOf: ["-D", "\(displayNumber)"])
        }
        arguments.append(temporaryURL.path)
        process.arguments = arguments

        let error = Pipe()
        process.standardError = error

        try process.run()
        process.waitUntilExit()

        let data = (try? Data(contentsOf: temporaryURL)) ?? Data()
        if process.terminationStatus == 0, !data.isEmpty {
            return data
        }

        let errorData = error.fileHandleForReading.readDataToEndOfFile()
        let message = String(data: errorData, encoding: .utf8) ?? "screencapture failed"
        throw CaptureError.failed(message)
    }

    public static func jpegData(from image: CGImage) throws -> Data {
        let data = NSMutableData()
        guard let destination = CGImageDestinationCreateWithData(data, "public.jpeg" as CFString, 1, nil) else {
            throw CaptureError.failed("CGImageDestinationCreateWithData failed")
        }
        CGImageDestinationAddImage(destination, image, [
            kCGImageDestinationLossyCompressionQuality: 0.72
        ] as CFDictionary)
        guard CGImageDestinationFinalize(destination) else {
            throw CaptureError.failed("CGImageDestinationFinalize failed")
        }
        return data as Data
    }
}

public enum CaptureError: LocalizedError {
    case failed(String)

    public var errorDescription: String? {
        switch self {
        case .failed(let message):
            return message
        }
    }
}
