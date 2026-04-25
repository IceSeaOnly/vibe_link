package com.vibelink.client;

public final class ScreenZoomController {
    private static final float FIT_ZOOM = 1f;
    private static final float DOUBLE_TAP_ZOOM = 2f;
    private static final float FIT_THRESHOLD = 1.05f;

    private ScreenZoomController() {
    }

    public static Result nextDoubleTapZoom(
        float currentZoom,
        float currentPanX,
        float currentPanY,
        float tapX,
        float tapY,
        float baseOffsetX,
        float baseOffsetY
    ) {
        if (currentZoom > FIT_THRESHOLD) {
            return new Result(FIT_ZOOM, 0f, 0f);
        }

        float nextPanX = focusedPan(currentZoom, currentPanX, tapX, baseOffsetX, DOUBLE_TAP_ZOOM);
        float nextPanY = focusedPan(currentZoom, currentPanY, tapY, baseOffsetY, DOUBLE_TAP_ZOOM);
        return new Result(DOUBLE_TAP_ZOOM, nextPanX, nextPanY);
    }

    private static float focusedPan(
        float currentZoom,
        float currentPan,
        float tap,
        float baseOffset,
        float targetZoom
    ) {
        float safeZoom = Math.max(currentZoom, FIT_ZOOM);
        float distanceFromBase = tap - baseOffset;
        return distanceFromBase - (distanceFromBase - currentPan) * (targetZoom / safeZoom);
    }

    public static final class Result {
        public final float zoom;
        public final float panX;
        public final float panY;

        public Result(float zoom, float panX, float panY) {
            this.zoom = zoom;
            this.panX = panX;
            this.panY = panY;
        }
    }
}
