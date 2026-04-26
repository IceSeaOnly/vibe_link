import AppKit
import ApplicationServices
import CoreGraphics
import Foundation

public enum InputController {
    public static func perform(_ request: ControlRequest) throws {
        try perform(request, captureSource: nil)
    }

    public static func perform(_ request: ControlRequest, captureSource: CaptureSource?) throws {
        if captureSource?.type == .window, shouldFocusWindowSource(for: request.type) {
            WindowFocusController().activate(source: captureSource)
        }
        switch request.type {
        case "move":
            try move(request, captureSource: captureSource)
        case "relativeMove":
            relativeMove(deltaX: request.deltaX ?? 0, deltaY: request.deltaY ?? 0, dragged: false)
        case "tap":
            try click(request, captureSource: captureSource, button: .left, count: 1)
        case "clickCurrent":
            clickCurrent(button: .left, count: 1)
        case "doubleTap":
            try click(request, captureSource: captureSource, button: .left, count: 2)
        case "doubleClickCurrent":
            clickCurrent(button: .left, count: 2)
        case "rightClick":
            try click(request, captureSource: captureSource, button: .right, count: 1)
        case "rightClickCurrent":
            clickCurrent(button: .right, count: 1)
        case "drag":
            try drag(request, captureSource: captureSource)
        case "mouseDown":
            try mouseDown(request, captureSource: captureSource)
        case "mouseDownCurrent":
            mouseDownCurrent()
        case "relativeDrag":
            relativeMove(deltaX: request.deltaX ?? 0, deltaY: request.deltaY ?? 0, dragged: true)
        case "mouseUp":
            try mouseUp(request, captureSource: captureSource)
        case "mouseUpCurrent":
            mouseUpCurrent()
        case "scroll":
            scroll(deltaX: request.deltaX ?? 0, deltaY: request.deltaY ?? 0)
        case "text":
            guard let text = request.text else { throw InputError.missingField("text") }
            try paste(text: text, submit: request.submit ?? false)
        case "clipboard":
            guard let text = request.text else { throw InputError.missingField("text") }
            writeClipboard(text)
        case "backspace":
            try pressKey(keyCode: 0x33, flags: [])
        case "enter":
            try pressKey(keyCode: 0x24, flags: [])
        case "cmdEnter":
            try pressKey(keyCode: 0x24, flags: .maskCommand)
        case "copy":
            try pressKey(keyCode: 0x08, flags: .maskCommand)
        case "paste":
            try pressKey(keyCode: 0x09, flags: .maskCommand)
        case "selectAll":
            try pressKey(keyCode: 0x00, flags: .maskCommand)
        case "escape":
            try pressKey(keyCode: 0x35, flags: [])
        case "interrupt":
            try pressKey(keyCode: 0x08, flags: .maskControl)
        case "undo":
            try pressKey(keyCode: 0x06, flags: .maskCommand)
        case "close":
            try pressKey(keyCode: 0x0D, flags: .maskCommand)
        default:
            throw InputError.unsupportedType(request.type)
        }
    }

    public static func runShortcutButton(_ button: ShortcutButton) throws {
        let request = ControlRequest(
            type: "tap",
            displayId: nil,
            x: button.x,
            y: button.y,
            fromX: nil,
            fromY: nil,
            toX: nil,
            toY: nil,
            durationMs: nil,
            deltaX: nil,
            deltaY: nil,
            text: nil,
            submit: nil,
            screenWidth: button.screenWidth,
            screenHeight: button.screenHeight
        )
        try perform(request)
    }

    private static func click(_ request: ControlRequest, captureSource: CaptureSource?, button: CGMouseButton, count: Int) throws {
        let point = try point(x: request.x, y: request.y, request: request, captureSource: captureSource)
        click(point: point, button: button, count: count)
    }

    private static func clickCurrent(button: CGMouseButton, count: Int) {
        click(point: currentMousePoint(), button: button, count: count)
    }

    private static func click(point: CGPoint, button: CGMouseButton, count: Int) {
        let downType: CGEventType = button == .right ? .rightMouseDown : .leftMouseDown
        let upType: CGEventType = button == .right ? .rightMouseUp : .leftMouseUp

        for clickIndex in 1...count {
            postMouse(type: downType, point: point, button: button, clickState: clickIndex)
            postMouse(type: upType, point: point, button: button, clickState: clickIndex)
            if count > 1 {
                usleep(80_000)
            }
        }
    }

    private static func move(_ request: ControlRequest, captureSource: CaptureSource?) throws {
        let point = try point(x: request.x, y: request.y, request: request, captureSource: captureSource)
        postMouse(type: .mouseMoved, point: point, button: .left, clickState: 0)
    }

    private static func relativeMove(deltaX: Int32, deltaY: Int32, dragged: Bool) {
        let current = currentMousePoint()
        let next = CGPoint(x: current.x + CGFloat(deltaX), y: current.y + CGFloat(deltaY))
        postMouse(type: dragged ? .leftMouseDragged : .mouseMoved, point: next, button: .left, clickState: dragged ? 1 : 0)
    }

    private static func mouseDownCurrent() {
        postMouse(type: .leftMouseDown, point: currentMousePoint(), button: .left, clickState: 1)
    }

    private static func mouseUpCurrent() {
        postMouse(type: .leftMouseUp, point: currentMousePoint(), button: .left, clickState: 1)
    }

    private static func mouseDown(_ request: ControlRequest, captureSource: CaptureSource?) throws {
        let point = try point(x: request.x, y: request.y, request: request, captureSource: captureSource)
        postMouse(type: .leftMouseDown, point: point, button: .left, clickState: 1)
    }

    private static func mouseUp(_ request: ControlRequest, captureSource: CaptureSource?) throws {
        let point = try point(x: request.x, y: request.y, request: request, captureSource: captureSource)
        postMouse(type: .leftMouseUp, point: point, button: .left, clickState: 1)
    }

    private static func drag(_ request: ControlRequest, captureSource: CaptureSource?) throws {
        let from = try point(x: request.fromX, y: request.fromY, request: request, captureSource: captureSource)
        let to = try point(x: request.toX, y: request.toY, request: request, captureSource: captureSource)
        let duration = max(request.durationMs ?? 250, 1)
        let steps = max(duration / 16, 1)

        postMouse(type: .leftMouseDown, point: from, button: .left, clickState: 1)
        for step in 1...steps {
            let progress = Double(step) / Double(steps)
            let x = from.x + (to.x - from.x) * progress
            let y = from.y + (to.y - from.y) * progress
            postMouse(type: .leftMouseDragged, point: CGPoint(x: x, y: y), button: .left, clickState: 1)
            usleep(useconds_t(duration * 1000 / steps))
        }
        postMouse(type: .leftMouseUp, point: to, button: .left, clickState: 1)
    }

    private static func scroll(deltaX: Int32, deltaY: Int32) {
        guard let event = CGEvent(scrollWheelEvent2Source: nil, units: .pixel, wheelCount: 2, wheel1: deltaY, wheel2: deltaX, wheel3: 0) else {
            return
        }
        event.post(tap: .cghidEventTap)
    }

    private static func paste(text: String, submit: Bool) throws {
        writeClipboard(text)
        guard AccessibilityPermission.isTrusted(prompt: true) else {
            throw InputError.accessibilityNotTrusted
        }
        postKey(keyCode: 0x09, flags: .maskCommand)
        if submit {
            usleep(80_000)
            postKey(keyCode: 0x24, flags: [])
        }
    }

    private static func pressKey(keyCode: CGKeyCode, flags: CGEventFlags) throws {
        guard AccessibilityPermission.isTrusted(prompt: true) else {
            throw InputError.accessibilityNotTrusted
        }
        postKey(keyCode: keyCode, flags: flags)
    }

    private static func writeClipboard(_ text: String) {
        let pasteboard = NSPasteboard.general
        pasteboard.clearContents()
        pasteboard.setString(text, forType: .string)
    }

    private static func point(x: Double?, y: Double?, request: ControlRequest, captureSource: CaptureSource?) throws -> CGPoint {
        guard let x, let y else { throw InputError.missingField("x/y") }
        guard let sourceWidth = request.screenWidth,
              let sourceHeight = request.screenHeight,
              sourceWidth > 0,
              sourceHeight > 0 else {
            return CGPoint(x: x, y: y)
        }

        if let captureSource {
            return mapPoint(x: x, y: y, sourceWidth: sourceWidth, sourceHeight: sourceHeight, captureSource: captureSource)
        }

        let screen = ScreenProvider.currentScreenInfo()
        if let displayId = request.displayId {
            return mapPoint(x: x, y: y, sourceWidth: sourceWidth, sourceHeight: sourceHeight, display: ScreenProvider.display(id: displayId))
        }

        let normalizedX = x / sourceWidth
        let normalizedY = y / sourceHeight
        return CGPoint(
            x: normalizedX * Double(screen.width),
            y: normalizedY * Double(screen.height)
        )
    }

    static func mapPoint(x: Double, y: Double, sourceWidth: Double, sourceHeight: Double, display: DisplayInfo) -> CGPoint {
        let normalizedX = x / sourceWidth
        let normalizedY = y / sourceHeight
        return CGPoint(
            x: Double(display.x) + normalizedX * Double(display.width),
            y: Double(display.y) + normalizedY * Double(display.height)
        )
    }

    static func mapPoint(x: Double, y: Double, sourceWidth: Double, sourceHeight: Double, captureSource: CaptureSource) -> CGPoint {
        let normalizedX = x / sourceWidth
        let normalizedY = y / sourceHeight
        return CGPoint(
            x: Double(captureSource.x) + normalizedX * Double(captureSource.width),
            y: Double(captureSource.y) + normalizedY * Double(captureSource.height)
        )
    }

    static func shouldFocusWindowSource(for type: String) -> Bool {
        switch type {
        case "tap", "clickCurrent",
            "doubleTap", "doubleClickCurrent",
            "rightClick", "rightClickCurrent",
            "drag", "mouseDown", "mouseDownCurrent",
            "mouseUp", "mouseUpCurrent",
            "text", "backspace", "enter", "cmdEnter",
            "copy", "paste", "selectAll", "escape",
            "interrupt", "undo", "close":
            return true
        default:
            return false
        }
    }

    private static func postMouse(type: CGEventType, point: CGPoint, button: CGMouseButton, clickState: Int) {
        guard let event = CGEvent(mouseEventSource: nil, mouseType: type, mouseCursorPosition: point, mouseButton: button) else {
            return
        }
        event.setIntegerValueField(.mouseEventClickState, value: Int64(clickState))
        event.post(tap: .cghidEventTap)
    }

    private static func currentMousePoint() -> CGPoint {
        CGEvent(source: nil)?.location ?? CGPoint.zero
    }

    private static func postKey(keyCode: CGKeyCode, flags: CGEventFlags) {
        guard let down = CGEvent(keyboardEventSource: nil, virtualKey: keyCode, keyDown: true),
              let up = CGEvent(keyboardEventSource: nil, virtualKey: keyCode, keyDown: false) else {
            return
        }
        down.flags = flags
        up.flags = flags
        down.post(tap: .cghidEventTap)
        up.post(tap: .cghidEventTap)
    }
}

public enum InputError: LocalizedError {
    case missingField(String)
    case unsupportedType(String)
    case accessibilityNotTrusted

    public var errorDescription: String? {
        switch self {
        case .missingField(let field):
            return "Missing required field: \(field)"
        case .unsupportedType(let type):
            return "Unsupported control type: \(type)"
        case .accessibilityNotTrusted:
            return "Keyboard input requires Accessibility permission for the app running VibeLinkServer. For Send Text, the text was still copied to the Mac clipboard. Enable it in System Settings > Privacy & Security > Accessibility, then retry."
        }
    }
}

public enum AccessibilityPermission {
    public static func isTrusted(prompt: Bool) -> Bool {
        let options = [kAXTrustedCheckOptionPrompt.takeRetainedValue() as String: prompt] as CFDictionary
        return AXIsProcessTrustedWithOptions(options)
    }
}
