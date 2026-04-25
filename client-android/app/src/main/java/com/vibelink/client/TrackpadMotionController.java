package com.vibelink.client;

public final class TrackpadMotionController {
    private TrackpadMotionController() {
    }

    public static Delta relativeDelta(float deltaX, float deltaY, float sensitivity) {
        return relativeDelta(deltaX, deltaY, sensitivity, 0L);
    }

    public static Delta relativeDelta(float deltaX, float deltaY, float sensitivity, long elapsedMs) {
        float multiplier = sensitivity * speedMultiplier(deltaX, deltaY, elapsedMs);
        return new Delta(
            Math.round(deltaX * multiplier),
            Math.round(deltaY * multiplier)
        );
    }

    private static float speedMultiplier(float deltaX, float deltaY, long elapsedMs) {
        if (elapsedMs <= 0L) return 1f;
        double distance = Math.hypot(deltaX, deltaY);
        double speed = distance / elapsedMs;
        if (speed <= 0.35d) return 1f;
        if (speed >= 2.0d) return 2.2f;
        return 1f + (float) ((speed - 0.35d) / 1.65d) * 1.2f;
    }

    public static Point initialCursor(int imageWidth, int imageHeight) {
        return new Point(
            Math.max(0, imageWidth / 2),
            Math.max(0, imageHeight / 2)
        );
    }

    public static Point moveCursor(int x, int y, int deltaX, int deltaY, int imageWidth, int imageHeight) {
        int maxX = Math.max(0, imageWidth - 1);
        int maxY = Math.max(0, imageHeight - 1);
        return new Point(
            clamp(x + deltaX, 0, maxX),
            clamp(y + deltaY, 0, maxY)
        );
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    public static final class Delta {
        public final int dx;
        public final int dy;

        public Delta(int dx, int dy) {
            this.dx = dx;
            this.dy = dy;
        }

        public boolean isEmpty() {
            return dx == 0 && dy == 0;
        }
    }

    public static final class Point {
        public final int x;
        public final int y;

        public Point(int x, int y) {
            this.x = x;
            this.y = y;
        }
    }
}
