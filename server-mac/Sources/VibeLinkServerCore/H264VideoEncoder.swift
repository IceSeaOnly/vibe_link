import AppKit
import CoreMedia
import CoreVideo
import Foundation
import VideoToolbox

public struct H264StreamSettings: Sendable {
    public let fps: Int
    public let bitRate: Int
    public let keyFrameInterval: Int

    public var frameDuration: CMTime {
        CMTime(value: 1, timescale: CMTimeScale(fps))
    }

    public var frameIntervalMilliseconds: Int {
        max(1, 1_000 / fps)
    }

    public static let lowLatency = H264StreamSettings(
        fps: 20,
        bitRate: 3_000_000,
        keyFrameInterval: 20
    )
}

public final class H264VideoEncoder {
    public let width: Int
    public let height: Int

    private var session: VTCompressionSession?
    private var frameIndex: Int64 = 0
    private let settings: H264StreamSettings
    private let lock = NSLock()
    private var pendingOutput: Data?
    private var pendingError: Error?
    private var pendingSemaphore: DispatchSemaphore?

    public init(width: Int, height: Int, settings: H264StreamSettings = .lowLatency) throws {
        self.width = width - (width % 2)
        self.height = height - (height % 2)
        self.settings = settings

        let status = VTCompressionSessionCreate(
            allocator: nil,
            width: Int32(self.width),
            height: Int32(self.height),
            codecType: kCMVideoCodecType_H264,
            encoderSpecification: nil,
            imageBufferAttributes: nil,
            compressedDataAllocator: nil,
            outputCallback: H264VideoEncoder.outputCallback,
            refcon: Unmanaged.passUnretained(self).toOpaque(),
            compressionSessionOut: &session
        )
        guard status == noErr, let session else {
            throw CaptureError.failed("VTCompressionSessionCreate failed: \(status)")
        }

        VTSessionSetProperty(session, key: kVTCompressionPropertyKey_RealTime, value: kCFBooleanTrue)
        VTSessionSetProperty(session, key: kVTCompressionPropertyKey_AllowFrameReordering, value: kCFBooleanFalse)
        VTSessionSetProperty(session, key: kVTCompressionPropertyKey_ProfileLevel, value: kVTProfileLevel_H264_Baseline_AutoLevel)
        VTSessionSetProperty(session, key: kVTCompressionPropertyKey_AverageBitRate, value: settings.bitRate as CFNumber)
        VTSessionSetProperty(session, key: kVTCompressionPropertyKey_ExpectedFrameRate, value: settings.fps as CFNumber)
        VTSessionSetProperty(session, key: kVTCompressionPropertyKey_MaxKeyFrameInterval, value: settings.keyFrameInterval as CFNumber)
        VTSessionSetProperty(session, key: kVTCompressionPropertyKey_MaxKeyFrameIntervalDuration, value: 1 as CFNumber)
        VTCompressionSessionPrepareToEncodeFrames(session)
    }

    deinit {
        if let session {
            VTCompressionSessionCompleteFrames(session, untilPresentationTimeStamp: .invalid)
            VTCompressionSessionInvalidate(session)
        }
    }

    public func encode(image: CGImage) throws -> Data? {
        guard let session else { return nil }
        let pixelBuffer = try makePixelBuffer(from: image)
        let semaphore = DispatchSemaphore(value: 0)
        lock.lock()
        pendingOutput = nil
        pendingError = nil
        pendingSemaphore = semaphore
        lock.unlock()

        let presentationTime = CMTime(value: frameIndex, timescale: CMTimeScale(settings.fps))
        let shouldForceKeyFrame = frameIndex % Int64(settings.keyFrameInterval) == 0
        frameIndex += 1
        let frameProperties = shouldForceKeyFrame
            ? [kVTEncodeFrameOptionKey_ForceKeyFrame: true] as CFDictionary
            : nil
        let status = VTCompressionSessionEncodeFrame(
            session,
            imageBuffer: pixelBuffer,
            presentationTimeStamp: presentationTime,
            duration: settings.frameDuration,
            frameProperties: frameProperties,
            sourceFrameRefcon: nil,
            infoFlagsOut: nil
        )
        guard status == noErr else {
            throw CaptureError.failed("VTCompressionSessionEncodeFrame failed: \(status)")
        }

        _ = semaphore.wait(timeout: .now() + .seconds(2))
        lock.lock()
        defer {
            pendingSemaphore = nil
            lock.unlock()
        }
        if let pendingError {
            throw pendingError
        }
        return pendingOutput
    }

    private func makePixelBuffer(from image: CGImage) throws -> CVPixelBuffer {
        var pixelBuffer: CVPixelBuffer?
        let attributes: [CFString: Any] = [
            kCVPixelBufferCGImageCompatibilityKey: true,
            kCVPixelBufferCGBitmapContextCompatibilityKey: true,
            kCVPixelBufferIOSurfacePropertiesKey: [:]
        ]
        let status = CVPixelBufferCreate(
            kCFAllocatorDefault,
            width,
            height,
            kCVPixelFormatType_32BGRA,
            attributes as CFDictionary,
            &pixelBuffer
        )
        guard status == kCVReturnSuccess, let pixelBuffer else {
            throw CaptureError.failed("CVPixelBufferCreate failed: \(status)")
        }

        CVPixelBufferLockBaseAddress(pixelBuffer, [])
        defer { CVPixelBufferUnlockBaseAddress(pixelBuffer, []) }
        guard let baseAddress = CVPixelBufferGetBaseAddress(pixelBuffer) else {
            throw CaptureError.failed("CVPixelBuffer base address unavailable")
        }
        guard let context = CGContext(
            data: baseAddress,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: CVPixelBufferGetBytesPerRow(pixelBuffer),
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.noneSkipFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue
        ) else {
            throw CaptureError.failed("CGContext for pixel buffer failed")
        }
        context.interpolationQuality = .none
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))
        return pixelBuffer
    }

    private func handle(status: OSStatus, sampleBuffer: CMSampleBuffer?) {
        lock.lock()
        defer {
            pendingSemaphore?.signal()
            lock.unlock()
        }
        guard status == noErr, let sampleBuffer else {
            pendingError = CaptureError.failed("H.264 encode callback failed: \(status)")
            return
        }
        guard CMSampleBufferDataIsReady(sampleBuffer) else {
            pendingError = CaptureError.failed("H.264 sample buffer is not ready")
            return
        }
        do {
            pendingOutput = try Self.annexBPayload(sampleBuffer: sampleBuffer)
        } catch {
            pendingError = error
        }
    }

    static func annexBPayload(sampleBuffer: CMSampleBuffer) throws -> Data {
        var nalUnits: [Data] = []
        let attachments = CMSampleBufferGetSampleAttachmentsArray(sampleBuffer, createIfNecessary: false) as? [[CFString: Any]]
        let isKeyFrame = attachments?.first?[kCMSampleAttachmentKey_NotSync] == nil
        if isKeyFrame, let formatDescription = CMSampleBufferGetFormatDescription(sampleBuffer) {
            if let sps = parameterSet(formatDescription: formatDescription, index: 0) {
                nalUnits.append(sps)
            }
            if let pps = parameterSet(formatDescription: formatDescription, index: 1) {
                nalUnits.append(pps)
            }
        }

        guard let blockBuffer = CMSampleBufferGetDataBuffer(sampleBuffer) else {
            throw CaptureError.failed("H.264 block buffer missing")
        }
        var totalLength = 0
        var dataPointer: UnsafeMutablePointer<Int8>?
        let status = CMBlockBufferGetDataPointer(
            blockBuffer,
            atOffset: 0,
            lengthAtOffsetOut: nil,
            totalLengthOut: &totalLength,
            dataPointerOut: &dataPointer
        )
        guard status == noErr, let dataPointer else {
            throw CaptureError.failed("CMBlockBufferGetDataPointer failed: \(status)")
        }

        var offset = 0
        while offset + 4 <= totalLength {
            let length = (UInt32(UInt8(bitPattern: dataPointer[offset])) << 24)
                | (UInt32(UInt8(bitPattern: dataPointer[offset + 1])) << 16)
                | (UInt32(UInt8(bitPattern: dataPointer[offset + 2])) << 8)
                | UInt32(UInt8(bitPattern: dataPointer[offset + 3]))
            offset += 4
            let nalLength = Int(length)
            guard nalLength > 0, offset + nalLength <= totalLength else { break }
            nalUnits.append(Data(bytes: dataPointer + offset, count: nalLength))
            offset += nalLength
        }
        return H264AnnexB.wrap(nalUnits: nalUnits)
    }

    private static func parameterSet(formatDescription: CMFormatDescription, index: Int) -> Data? {
        var pointer: UnsafePointer<UInt8>?
        var size = 0
        var count = 0
        var headerLength: Int32 = 0
        let status = CMVideoFormatDescriptionGetH264ParameterSetAtIndex(
            formatDescription,
            parameterSetIndex: index,
            parameterSetPointerOut: &pointer,
            parameterSetSizeOut: &size,
            parameterSetCountOut: &count,
            nalUnitHeaderLengthOut: &headerLength
        )
        guard status == noErr, let pointer, size > 0 else { return nil }
        return Data(bytes: pointer, count: size)
    }

    private static let outputCallback: VTCompressionOutputCallback = { refcon, _, status, _, sampleBuffer in
        guard let refcon else { return }
        let encoder = Unmanaged<H264VideoEncoder>.fromOpaque(refcon).takeUnretainedValue()
        encoder.handle(status: status, sampleBuffer: sampleBuffer)
    }
}
