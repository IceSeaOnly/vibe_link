import AppKit
import CoreGraphics
import CoreImage
import CoreMedia
import Foundation
import ScreenCaptureKit

public protocol CaptureSourceProviding: Sendable {
    func sources() -> [CaptureSource]
}

public protocol CaptureSourceActivating: Sendable {
    func activate(source: CaptureSource?)
}

public struct StaticCaptureSourceProvider: CaptureSourceProviding {
    private let values: [CaptureSource]

    public init(sources: [CaptureSource]) {
        self.values = sources
    }

    public func sources() -> [CaptureSource] {
        values
    }
}

public final class CaptureSourceManager: @unchecked Sendable {
    private let provider: CaptureSourceProviding
    private let activator: CaptureSourceActivating
    private let lock = NSLock()
    private var selectedId: String?

    public init(provider: CaptureSourceProviding = SystemCaptureSourceProvider(), activator: CaptureSourceActivating = WindowFocusController()) {
        self.provider = provider
        self.activator = activator
    }

    public func response() -> CaptureSourcesResponse {
        let sources = provider.sources()
        return CaptureSourcesResponse(ok: true, sources: sources, selected: selectedSource(from: sources))
    }

    public func selectedSource() -> CaptureSource? {
        selectedSource(from: provider.sources())
    }

    public func select(id: String) throws -> CaptureSourcesResponse {
        let sources = provider.sources()
        guard sources.contains(where: { $0.id == id }) else {
            throw CaptureError.failed("Unknown capture source id")
        }
        lock.lock()
        selectedId = id
        lock.unlock()
        let selected = sources.first(where: { $0.id == id })
        activator.activate(source: selected)
        return CaptureSourcesResponse(ok: true, sources: sources, selected: selected)
    }

    private func selectedSource(from sources: [CaptureSource]) -> CaptureSource? {
        lock.lock()
        let id = selectedId
        lock.unlock()
        if let id, let source = sources.first(where: { $0.id == id }) {
            return source
        }
        return sources.first(where: { $0.type == .display && $0.isMain }) ?? sources.first
    }
}

public struct WindowFocusController: CaptureSourceActivating {
    public init() {}

    public func activate(source: CaptureSource?) {
        guard let source,
              source.type == .window,
              let windowID = CGWindowID(source.id.removingPrefix("window:")) else {
            return
        }
        Self.activate(windowID: windowID)
    }

    static func activate(windowID: CGWindowID) {
        let info = windowInfo(windowID: windowID)
        if let pid = ownerProcessIdentifier(windowID: windowID, from: info) {
            raiseWindow(windowID: windowID, ownerPID: pid, info: info)
            NSRunningApplication(processIdentifier: pid)?.activate(options: [.activateIgnoringOtherApps])
        }
    }

    static func ownerProcessIdentifier(windowID: CGWindowID, from infoList: [[String: Any]]) -> pid_t? {
        for info in infoList {
            guard let number = uint32Value(info[kCGWindowNumber as String]), number == windowID else {
                continue
            }
            return pidValue(info[kCGWindowOwnerPID as String])
        }
        return nil
    }

    private static func windowInfo(windowID: CGWindowID) -> [[String: Any]] {
        guard let values = CGWindowListCopyWindowInfo([.optionIncludingWindow], windowID) as? [[String: Any]] else {
            return []
        }
        return values
    }

    private static func raiseWindow(windowID: CGWindowID, ownerPID: pid_t, info: [[String: Any]]) {
        guard AXIsProcessTrusted(),
              let title = windowTitle(windowID: windowID, from: info),
              !title.isEmpty else {
            return
        }
        let app = AXUIElementCreateApplication(ownerPID)
        var rawWindows: CFTypeRef?
        guard AXUIElementCopyAttributeValue(app, kAXWindowsAttribute as CFString, &rawWindows) == .success,
              let windows = rawWindows as? [AXUIElement] else {
            return
        }
        for window in windows {
            var rawTitle: CFTypeRef?
            guard AXUIElementCopyAttributeValue(window, kAXTitleAttribute as CFString, &rawTitle) == .success,
                  let axTitle = rawTitle as? String,
                  axTitle == title else {
                continue
            }
            AXUIElementPerformAction(window, kAXRaiseAction as CFString)
            return
        }
    }

    private static func windowTitle(windowID: CGWindowID, from infoList: [[String: Any]]) -> String? {
        for info in infoList {
            guard let number = uint32Value(info[kCGWindowNumber as String]), number == windowID else {
                continue
            }
            return info[kCGWindowName as String] as? String
        }
        return nil
    }

    private static func uint32Value(_ value: Any?) -> UInt32? {
        switch value {
        case let value as UInt32:
            return value
        case let value as Int:
            return UInt32(value)
        case let value as NSNumber:
            return value.uint32Value
        default:
            return nil
        }
    }

    private static func pidValue(_ value: Any?) -> pid_t? {
        switch value {
        case let value as pid_t:
            return value
        case let value as Int:
            return pid_t(value)
        case let value as NSNumber:
            return value.int32Value
        default:
            return nil
        }
    }
}

public struct SystemCaptureSourceProvider: CaptureSourceProviding {
    public init() {}

    public func sources() -> [CaptureSource] {
        let displays = ScreenProvider.displays().map { display in
            CaptureSource(
                id: "display:\(display.id)",
                type: .display,
                name: display.name,
                appName: nil,
                x: display.x,
                y: display.y,
                width: display.width,
                height: display.height,
                scale: display.scale,
                isMain: display.isMain
            )
        }
        return displays + windowSources()
    }

    private func windowSources() -> [CaptureSource] {
        if #available(macOS 13.0, *) {
            let semaphore = DispatchSemaphore(value: 0)
            let box = LockedBox<[CaptureSource]>([])
            Task {
                do {
                    let content = try await SCShareableContent.excludingDesktopWindows(false, onScreenWindowsOnly: true)
                    let windows = content.windows
                        .filter { window in
                            window.frame.width >= 80 &&
                                window.frame.height >= 60 &&
                                !(window.title?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ?? true)
                        }
                        .prefix(80)
                        .map { window in
                            let appName = window.owningApplication?.applicationName
                            let title = window.title ?? "Window \(window.windowID)"
                            let name = appName.map { "\($0) - \(title)" } ?? title
                            return CaptureSource(
                                id: "window:\(window.windowID)",
                                type: .window,
                                name: name,
                                appName: appName,
                                x: Int(window.frame.origin.x),
                                y: Int(window.frame.origin.y),
                                width: Int(window.frame.width),
                                height: Int(window.frame.height),
                                scale: 1,
                                isMain: false
                            )
                        }
                    box.set(Array(windows))
                } catch {
                    box.set([])
                }
                semaphore.signal()
            }
            _ = semaphore.wait(timeout: .now() + .seconds(2))
            return box.get()
        }
        return []
    }
}

enum CaptureImageResolver {
    static func captureImage(source: CaptureSource?, fallbackDisplayId: String?) throws -> CGImage {
        guard let source else {
            return try ScreenCapture.captureImage(displayId: fallbackDisplayId)
        }
        switch source.type {
        case .display:
            return try ScreenCapture.captureImage(displayId: source.id.removingPrefix("display:"))
        case .window:
            return try WindowCapture.captureImage(windowID: source.id.removingPrefix("window:"))
        }
    }

    static func captureJPEG(source: CaptureSource?, fallbackDisplayId: String?) throws -> Data {
        guard let source else {
            return try ScreenCapture.captureJPEG(displayId: fallbackDisplayId)
        }
        switch source.type {
        case .display:
            return try ScreenCapture.captureJPEG(displayId: source.id.removingPrefix("display:"))
        case .window:
            let image = try WindowCapture.captureImage(windowID: source.id.removingPrefix("window:"))
            return try ScreenCapture.jpegData(from: image)
        }
    }
}

enum WindowCapture {
    private static let streamCache = WindowStreamCache<WindowFrameStream>()

    static func captureImage(windowID rawID: String) throws -> CGImage {
        guard #available(macOS 13.0, *), let windowID = UInt32(rawID) else {
            throw CaptureError.failed("Window capture requires macOS 13 and a valid window id")
        }
        let stream = try streamCache.value(for: windowID) {
            try WindowFrameStream.started(windowID: windowID)
        }
        return try stream.latestImage()
    }
}

final class WindowStreamCache<Stream>: @unchecked Sendable {
    private let lock = NSLock()
    private var streams: [CGWindowID: Stream] = [:]

    var count: Int {
        lock.lock()
        defer { lock.unlock() }
        return streams.count
    }

    func value(for windowID: CGWindowID, create: () throws -> Stream) throws -> Stream {
        lock.lock()
        if let stream = streams[windowID] {
            lock.unlock()
            return stream
        }
        lock.unlock()

        let stream = try create()
        lock.lock()
        if let existing = streams[windowID] {
            lock.unlock()
            return existing
        }
        streams[windowID] = stream
        lock.unlock()
        return stream
    }

    func removeAll() {
        lock.lock()
        streams.removeAll()
        lock.unlock()
    }
}

@available(macOS 13.0, *)
private final class WindowFrameStream: NSObject, SCStreamOutput, SCStreamDelegate {
    private let windowID: CGWindowID
    private let frameSemaphore = DispatchSemaphore(value: 0)
    private let lock = NSLock()
    private let ciContext = CIContext()
    private var stream: SCStream?
    private var frame: CVPixelBuffer?
    private var frameWidth = 0
    private var frameHeight = 0
    private var error: Error?

    private init(windowID: CGWindowID) {
        self.windowID = windowID
        super.init()
    }

    deinit {
        let stream = stream
        Task { try? await stream?.stopCapture() }
    }

    static func started(windowID: CGWindowID) throws -> WindowFrameStream {
        let receiver = WindowFrameStream(windowID: windowID)
        try receiver.start()
        return receiver
    }

    private func start() throws {
        let semaphore = DispatchSemaphore(value: 0)
        let result = LockedBox<Result<Void, Error>?>(nil)
        Task {
            do {
                try await startAsync()
                result.set(.success(()))
            } catch {
                result.set(.failure(error))
            }
            semaphore.signal()
        }
        _ = semaphore.wait(timeout: .now() + .seconds(3))
        switch result.get() {
        case .success:
            break
        case .failure(let error):
            throw error
        case nil:
            throw CaptureError.failed("Window capture setup timed out")
        }
        _ = try waitForFrame(timeout: 2)
    }

    private func startAsync() async throws {
        let content = try await SCShareableContent.excludingDesktopWindows(false, onScreenWindowsOnly: true)
        guard let window = content.windows.first(where: { $0.windowID == windowID }) else {
            throw CaptureError.failed("Window is no longer available")
        }
        let configuration = SCStreamConfiguration()
        configuration.width = max(2, Int(window.frame.width)) & ~1
        configuration.height = max(2, Int(window.frame.height)) & ~1
        configuration.minimumFrameInterval = CMTime(value: 1, timescale: 20)
        configuration.queueDepth = 2
        configuration.capturesAudio = false
        configuration.pixelFormat = kCVPixelFormatType_32BGRA
        configuration.showsCursor = true

        setFrameSize(width: configuration.width, height: configuration.height)

        let stream = SCStream(filter: SCContentFilter(desktopIndependentWindow: window), configuration: configuration, delegate: self)
        try stream.addStreamOutput(self, type: .screen, sampleHandlerQueue: DispatchQueue(label: "vibelink.window.capture.\(windowID)"))
        try await stream.startCapture()
        self.stream = stream
    }

    func latestImage() throws -> CGImage {
        let pixelBuffer = try waitForFrame(timeout: 2)
        lock.lock()
        let width = frameWidth
        let height = frameHeight
        lock.unlock()
        return try ciContext.createCGImage(CIImage(cvPixelBuffer: pixelBuffer), from: CGRect(x: 0, y: 0, width: width, height: height))
            ?? {
                throw CaptureError.failed("CIContext failed to create window image")
            }()
    }

    private func setFrameSize(width: Int, height: Int) {
        lock.lock()
        frameWidth = width
        frameHeight = height
        lock.unlock()
    }

    func stream(_ stream: SCStream, didOutputSampleBuffer sampleBuffer: CMSampleBuffer, of type: SCStreamOutputType) {
        guard type == .screen else { return }
        guard CMSampleBufferDataIsReady(sampleBuffer),
              let pixelBuffer = CMSampleBufferGetImageBuffer(sampleBuffer) else {
            return
        }
        lock.lock()
        frame = pixelBuffer
        frameSemaphore.signal()
        lock.unlock()
    }

    func stream(_ stream: SCStream, didStopWithError error: Error) {
        lock.lock()
        self.error = error
        frameSemaphore.signal()
        lock.unlock()
    }

    private func waitForFrame(timeout: Int) throws -> CVPixelBuffer {
        lock.lock()
        if let error {
            lock.unlock()
            throw error
        }
        if let frame {
            lock.unlock()
            return frame
        }
        lock.unlock()

        guard frameSemaphore.wait(timeout: .now() + .seconds(timeout)) == .success else {
            throw CaptureError.failed("No window frame received")
        }
        lock.lock()
        defer { lock.unlock() }
        if let error { throw error }
        guard let frame else {
            throw CaptureError.failed("No window frame received")
        }
        return frame
    }
}

private final class LockedBox<Value>: @unchecked Sendable {
    private let lock = NSLock()
    private var value: Value

    init(_ value: Value) {
        self.value = value
    }

    func get() -> Value {
        lock.lock()
        defer { lock.unlock() }
        return value
    }

    func set(_ next: Value) {
        lock.lock()
        value = next
        lock.unlock()
    }
}

private extension String {
    func removingPrefix(_ prefix: String) -> String {
        hasPrefix(prefix) ? String(dropFirst(prefix.count)) : self
    }
}
