package com.vibelink.client;

public final class TrackpadMotionControllerTest {
    public static void main(String[] args) {
        mapsSmallFingerMovementToRelativeMouseDelta();
        ignoresSubPixelMovementAfterScaling();
        acceleratesFastFingerMovement();
        movesVirtualCursorWithinImageBounds();
        centersVirtualCursorWhenMissing();
    }

    private static void mapsSmallFingerMovementToRelativeMouseDelta() {
        TrackpadMotionController.Delta delta = TrackpadMotionController.relativeDelta(8f, -5f, 1.5f);

        assertEquals(12, delta.dx, "dx");
        assertEquals(-8, delta.dy, "dy");
    }

    private static void ignoresSubPixelMovementAfterScaling() {
        TrackpadMotionController.Delta delta = TrackpadMotionController.relativeDelta(0.2f, 0.2f, 1.5f);

        assertEquals(0, delta.dx, "dx");
        assertEquals(0, delta.dy, "dy");
    }

    private static void acceleratesFastFingerMovement() {
        TrackpadMotionController.Delta slow = TrackpadMotionController.relativeDelta(20f, 0f, 1.5f, 120L);
        TrackpadMotionController.Delta fast = TrackpadMotionController.relativeDelta(20f, 0f, 1.5f, 12L);

        if (fast.dx <= slow.dx) {
            throw new AssertionError("fast movement should be accelerated");
        }
    }

    private static void movesVirtualCursorWithinImageBounds() {
        TrackpadMotionController.Point point = TrackpadMotionController.moveCursor(95, 5, 20, -20, 100, 80);

        assertEquals(99, point.x, "x");
        assertEquals(0, point.y, "y");
    }

    private static void centersVirtualCursorWhenMissing() {
        TrackpadMotionController.Point point = TrackpadMotionController.initialCursor(101, 81);

        assertEquals(50, point.x, "x");
        assertEquals(40, point.y, "y");
    }

    private static void assertEquals(int expected, int actual, String name) {
        if (expected != actual) {
            throw new AssertionError(name + " expected " + expected + " but was " + actual);
        }
    }
}
