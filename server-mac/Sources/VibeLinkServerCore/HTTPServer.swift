import Foundation
import CryptoKit
import Network

public protocol HTTPRouting: Sendable {
    func route(_ request: HTTPRequest) async -> HTTPResponse
}

public final class HTTPServer {
    private let config: ServerConfig
    private let router: any HTTPRouting
    private let publishesBonjour: Bool
    private let queue = DispatchQueue(label: "vibelink.http.server")
    private var listener: NWListener?

    public init(config: ServerConfig, router: any HTTPRouting, publishesBonjour: Bool = true) {
        self.config = config
        self.router = router
        self.publishesBonjour = publishesBonjour
    }

    public func start() throws {
        let listener = try NWListener(using: .tcp, on: NWEndpoint.Port(rawValue: config.port)!)
        if publishesBonjour {
            let hostName = Host.current().localizedName ?? "Mac"
            listener.service = NWListener.Service(name: "VibeLink \(hostName)", type: "_vibelink._tcp")
        }
        self.listener = listener
        let startup = DispatchSemaphore(value: 0)
        var startupError: NWError?

        listener.newConnectionHandler = { [weak self] connection in
            self?.handle(connection)
        }
        listener.stateUpdateHandler = { state in
            switch state {
            case .ready:
                startup.signal()
            case .failed(let error):
                startupError = error
                print("HTTP listener failed: \(error)")
                startup.signal()
            default:
                break
            }
        }
        listener.start(queue: queue)

        _ = startup.wait(timeout: .now() + .seconds(3))
        if let startupError {
            throw ServerError.listenerFailed(startupError)
        }
    }

    private func handle(_ connection: NWConnection) {
        connection.start(queue: queue)
        readRequest(from: connection, buffer: Data())
    }

    private func readRequest(from connection: NWConnection, buffer: Data) {
        connection.receive(minimumIncompleteLength: 1, maximumLength: 64 * 1024) { [weak self] data, _, isComplete, error in
            guard let self else { return }
            if let error {
                print("Connection read failed: \(error)")
                connection.cancel()
                return
            }
            if isComplete {
                connection.cancel()
                return
            }

            var nextBuffer = buffer
            if let data {
                nextBuffer.append(data)
            }

            if let expectedLength = HTTPParser.expectedLength(nextBuffer), nextBuffer.count >= expectedLength {
                guard let request = HTTPParser.parse(nextBuffer) else {
                    self.send(.text("Bad Request", status: 400, reason: "Bad Request"), on: connection)
                    return
                }
                if request.method == "GET", request.path == "/stream" {
                    self.handleStream(request, connection: connection)
                    return
                }
                if request.method == "GET", request.path == "/stream-ws" {
                    self.handleWebSocketStream(request, connection: connection)
                    return
                }
                if request.method == "GET", request.path == "/stream-h264" {
                    self.handleH264Stream(request, connection: connection)
                    return
                }
                Task {
                    let response = await self.router.route(request)
                    self.send(response, on: connection)
                }
                return
            }

            self.readRequest(from: connection, buffer: nextBuffer)
        }
    }

    private func send(_ response: HTTPResponse, on connection: NWConnection) {
        connection.send(content: response.serialized(), completion: .contentProcessed { error in
            if let error {
                print("Connection write failed: \(error)")
            }
            connection.cancel()
        })
    }

    private func handleStream(_ request: HTTPRequest, connection: NWConnection) {
        guard Auth.isStreamAuthorized(request: request, config: config) else {
            send(.json(ErrorResponse(ok: false, error: "Unauthorized"), status: 401, reason: "Unauthorized"), on: connection)
            return
        }

        let header = """
        HTTP/1.1 200 OK\r
        Content-Type: multipart/x-mixed-replace; boundary=frame\r
        Cache-Control: no-cache\r
        Connection: close\r
        \r

        """
        connection.send(content: Data(header.utf8), completion: .contentProcessed { [weak self] error in
            guard error == nil else {
                connection.cancel()
                return
            }
            self?.sendNextFrame(on: connection, displayId: request.query["displayId"])
        })
    }

    private func sendNextFrame(on connection: NWConnection, displayId: String?) {
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self else { return }
            do {
                let jpeg = try ScreenCapture.captureJPEG(displayId: displayId)
                var frame = Data()
                frame.append(Data("--frame\r\n".utf8))
                frame.append(Data("Content-Type: image/jpeg\r\n".utf8))
                frame.append(Data("Content-Length: \(jpeg.count)\r\n\r\n".utf8))
                frame.append(jpeg)
                frame.append(Data("\r\n".utf8))

                connection.send(content: frame, completion: .contentProcessed { [weak self] error in
                    if let error {
                        print("Stream write failed: \(error)")
                        connection.cancel()
                        return
                    }
                    self?.queue.asyncAfter(deadline: .now() + .milliseconds(250)) {
                        self?.sendNextFrame(on: connection, displayId: displayId)
                    }
                })
            } catch {
                print("Screen capture failed: \(error.localizedDescription)")
                connection.cancel()
            }
        }
    }

    private func handleWebSocketStream(_ request: HTTPRequest, connection: NWConnection) {
        guard Auth.isStreamAuthorized(request: request, config: config) else {
            send(.json(ErrorResponse(ok: false, error: "Unauthorized"), status: 401, reason: "Unauthorized"), on: connection)
            return
        }
        guard request.headers["upgrade"]?.lowercased() == "websocket",
              let key = request.headers["sec-websocket-key"],
              let accept = webSocketAccept(key: key) else {
            send(.text("Bad WebSocket Request", status: 400, reason: "Bad Request"), on: connection)
            return
        }

        let header = """
        HTTP/1.1 101 Switching Protocols\r
        Upgrade: websocket\r
        Connection: Upgrade\r
        Sec-WebSocket-Accept: \(accept)\r
        Cache-Control: no-cache\r
        \r

        """
        connection.send(content: Data(header.utf8), completion: .contentProcessed { [weak self] error in
            guard error == nil else {
                connection.cancel()
                return
            }
            self?.sendNextWebSocketFrame(on: connection, displayId: request.query["displayId"])
        })
    }

    private func sendNextWebSocketFrame(on connection: NWConnection, displayId: String?) {
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self else { return }
            do {
                let jpeg = try ScreenCapture.captureJPEG(displayId: displayId)
                let frame = self.webSocketBinaryFrame(payload: jpeg)
                connection.send(content: frame, completion: .contentProcessed { [weak self] error in
                    if let error {
                        print("WebSocket stream write failed: \(error)")
                        connection.cancel()
                        return
                    }
                    self?.queue.asyncAfter(deadline: .now() + .milliseconds(100)) {
                        self?.sendNextWebSocketFrame(on: connection, displayId: displayId)
                    }
                })
            } catch {
                print("WebSocket screen capture failed: \(error.localizedDescription)")
                connection.cancel()
            }
        }
    }

    private func handleH264Stream(_ request: HTTPRequest, connection: NWConnection) {
        guard Auth.isStreamAuthorized(request: request, config: config) else {
            send(.json(ErrorResponse(ok: false, error: "Unauthorized"), status: 401, reason: "Unauthorized"), on: connection)
            return
        }
        guard request.headers["upgrade"]?.lowercased() == "websocket",
              let key = request.headers["sec-websocket-key"],
              let accept = webSocketAccept(key: key) else {
            send(.text("Bad WebSocket Request", status: 400, reason: "Bad Request"), on: connection)
            return
        }

        let header = """
        HTTP/1.1 101 Switching Protocols\r
        Upgrade: websocket\r
        Connection: Upgrade\r
        Sec-WebSocket-Accept: \(accept)\r
        Cache-Control: no-cache\r
        X-VibeLink-Video: h264-annexb\r
        \r

        """
        connection.send(content: Data(header.utf8), completion: .contentProcessed { [weak self] error in
            guard error == nil else {
                connection.cancel()
                return
            }
            self?.startH264Frames(on: connection, displayId: request.query["displayId"])
        })
    }

    private func startH264Frames(on connection: NWConnection, displayId: String?) {
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self else { return }
            do {
                let display = ScreenProvider.display(id: displayId)
                let settings = H264StreamSettings.lowLatency
                let encoder = try H264VideoEncoder(width: display.width, height: display.height, settings: settings)
                self.sendNextH264Frame(on: connection, displayId: displayId, encoder: encoder, settings: settings)
            } catch {
                print("H.264 stream setup failed: \(error.localizedDescription)")
                connection.cancel()
            }
        }
    }

    private func sendNextH264Frame(on connection: NWConnection, displayId: String?, encoder: H264VideoEncoder, settings: H264StreamSettings) {
        DispatchQueue.global(qos: .userInitiated).async { [weak self] in
            guard let self else { return }
            do {
                let image = try ScreenCapture.captureImage(displayId: displayId)
                guard let payload = try encoder.encode(image: image), !payload.isEmpty else {
                    self.queue.asyncAfter(deadline: .now() + .milliseconds(settings.frameIntervalMilliseconds)) {
                        self.sendNextH264Frame(on: connection, displayId: displayId, encoder: encoder, settings: settings)
                    }
                    return
                }
                let frame = self.webSocketBinaryFrame(payload: payload)
                connection.send(content: frame, completion: .contentProcessed { [weak self] error in
                    if let error {
                        print("H.264 stream write failed: \(error)")
                        connection.cancel()
                        return
                    }
                    self?.queue.asyncAfter(deadline: .now() + .milliseconds(settings.frameIntervalMilliseconds)) {
                        self?.sendNextH264Frame(on: connection, displayId: displayId, encoder: encoder, settings: settings)
                    }
                })
            } catch {
                print("H.264 screen capture failed: \(error.localizedDescription)")
                connection.cancel()
            }
        }
    }

    private func webSocketAccept(key: String) -> String? {
        let magic = key.trimmingCharacters(in: .whitespacesAndNewlines) + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        guard let data = magic.data(using: .utf8) else { return nil }
        let digest = Insecure.SHA1.hash(data: data)
        return Data(digest).base64EncodedString()
    }

    private func webSocketBinaryFrame(payload: Data) -> Data {
        var frame = Data()
        frame.append(0x82)
        if payload.count < 126 {
            frame.append(UInt8(payload.count))
        } else if payload.count <= UInt16.max {
            frame.append(126)
            frame.append(UInt8((payload.count >> 8) & 0xff))
            frame.append(UInt8(payload.count & 0xff))
        } else {
            frame.append(127)
            let length = UInt64(payload.count)
            for shift in stride(from: 56, through: 0, by: -8) {
                frame.append(UInt8((length >> UInt64(shift)) & 0xff))
            }
        }
        frame.append(payload)
        return frame
    }
}

public enum ServerError: LocalizedError {
    case listenerFailed(NWError)

    public var errorDescription: String? {
        switch self {
        case .listenerFailed(let error):
            return "HTTP listener failed: \(error)"
        }
    }
}
