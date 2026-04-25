import Foundation
import Network

public protocol HTTPRouting: Sendable {
    func route(_ request: HTTPRequest) async -> HTTPResponse
}

public final class HTTPServer {
    private let config: ServerConfig
    private let router: any HTTPRouting
    private let queue = DispatchQueue(label: "vibelink.http.server")
    private var listener: NWListener?

    public init(config: ServerConfig, router: any HTTPRouting) {
        self.config = config
        self.router = router
    }

    public func start() throws {
        let listener = try NWListener(using: .tcp, on: NWEndpoint.Port(rawValue: config.port)!)
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
