import Foundation

public struct HTTPRequest: Sendable {
    public let method: String
    public let path: String
    public let query: [String: String]
    public let headers: [String: String]
    public let body: Data

    public init(method: String, path: String, query: [String: String], headers: [String: String], body: Data) {
        self.method = method
        self.path = path
        self.query = query
        self.headers = headers
        self.body = body
    }
}

public struct HTTPResponse: Sendable {
    public let status: Int
    public let reason: String
    public let headers: [String: String]
    public let body: Data

    public init(status: Int, reason: String, headers: [String: String] = [:], body: Data = Data()) {
        self.status = status
        self.reason = reason
        self.headers = headers
        self.body = body
    }

    public static func json<T: Encodable>(_ value: T, status: Int = 200, reason: String = "OK", encoder: JSONEncoder = JSONCoding.encoder) -> HTTPResponse {
        do {
            let data = try encoder.encode(value)
            return HTTPResponse(status: status, reason: reason, headers: ["Content-Type": "application/json; charset=utf-8"], body: data)
        } catch {
            return HTTPResponse.text("JSON encoding failed: \(error)", status: 500, reason: "Internal Server Error")
        }
    }

    public static func text(_ text: String, status: Int, reason: String) -> HTTPResponse {
        HTTPResponse(status: status, reason: reason, headers: ["Content-Type": "text/plain; charset=utf-8"], body: Data(text.utf8))
    }

    public static func html(_ html: String, status: Int = 200, reason: String = "OK") -> HTTPResponse {
        HTTPResponse(status: status, reason: reason, headers: ["Content-Type": "text/html; charset=utf-8"], body: Data(html.utf8))
    }

    public static func png(_ data: Data, status: Int = 200, reason: String = "OK") -> HTTPResponse {
        HTTPResponse(status: status, reason: reason, headers: ["Content-Type": "image/png", "Cache-Control": "no-store"], body: data)
    }

    public func serialized() -> Data {
        var lines = ["HTTP/1.1 \(status) \(reason)"]
        var mergedHeaders = headers
        mergedHeaders["Content-Length"] = "\(body.count)"
        mergedHeaders["Connection"] = "close"
        for (key, value) in mergedHeaders {
            lines.append("\(key): \(value)")
        }
        lines.append("")
        lines.append("")

        var data = Data(lines.joined(separator: "\r\n").utf8)
        data.append(body)
        return data
    }
}

public enum HTTPParser {
    public static func parse(_ data: Data) -> HTTPRequest? {
        guard let headerRange = data.range(of: Data("\r\n\r\n".utf8)),
              let headerText = String(data: data[..<headerRange.lowerBound], encoding: .utf8) else {
            return nil
        }

        let lines = headerText.components(separatedBy: "\r\n")
        guard let requestLine = lines.first else { return nil }
        let parts = requestLine.split(separator: " ", maxSplits: 2).map(String.init)
        guard parts.count >= 2 else { return nil }

        let urlParts = parts[1].split(separator: "?", maxSplits: 1).map(String.init)
        let path = urlParts[0]
        let query = urlParts.count > 1 ? parseQuery(urlParts[1]) : [:]
        var headers: [String: String] = [:]
        for line in lines.dropFirst() {
            guard let colon = line.firstIndex(of: ":") else { continue }
            let key = String(line[..<colon]).trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            let value = String(line[line.index(after: colon)...]).trimmingCharacters(in: .whitespacesAndNewlines)
            headers[key] = value
        }

        let bodyStart = headerRange.upperBound
        let body = data[bodyStart...]
        return HTTPRequest(method: parts[0], path: path, query: query, headers: headers, body: Data(body))
    }

    public static func expectedLength(_ data: Data) -> Int? {
        guard let headerRange = data.range(of: Data("\r\n\r\n".utf8)),
              let headerText = String(data: data[..<headerRange.lowerBound], encoding: .utf8) else {
            return nil
        }

        var contentLength = 0
        for line in headerText.components(separatedBy: "\r\n").dropFirst() {
            guard let colon = line.firstIndex(of: ":") else { continue }
            let key = String(line[..<colon]).trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            if key == "content-length" {
                contentLength = Int(String(line[line.index(after: colon)...]).trimmingCharacters(in: .whitespacesAndNewlines)) ?? 0
            }
        }
        return headerRange.upperBound + contentLength
    }

    private static func parseQuery(_ raw: String) -> [String: String] {
        var values: [String: String] = [:]
        for pair in raw.split(separator: "&") {
            let parts = pair.split(separator: "=", maxSplits: 1).map(String.init)
            let key = parts[0].removingPercentEncoding ?? parts[0]
            let value = parts.count > 1 ? (parts[1].removingPercentEncoding ?? parts[1]) : ""
            values[key] = value
        }
        return values
    }
}

public enum Auth {
    public static func isAuthorized(request: HTTPRequest, config: ServerConfig) -> Bool {
        request.headers["authorization"] == "Bearer \(config.token)"
    }

    public static func isStreamAuthorized(request: HTTPRequest, config: ServerConfig) -> Bool {
        request.query["token"] == config.token
    }
}

public enum JSONCoding {
    public static let encoder: JSONEncoder = {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }()

    public static let decoder: JSONDecoder = {
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        return decoder
    }()
}
