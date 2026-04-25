package com.vibelink.client;

public final class ScreenZoomControllerTest {
    public static void main(String[] args) {
        doubleTapZoomsInWhenAlreadyFitToWidth();
        doubleTapResetsWhenAlreadyZoomedIn();
    }

    private static void doubleTapZoomsInWhenAlreadyFitToWidth() {
        ScreenZoomController.Result result = ScreenZoomController.nextDoubleTapZoom(
            1f,
            0f,
            0f,
            180f,
            120f,
            0f,
            40f
        );

        assertClose(2f, result.zoom, "zoom");
        assertClose(-180f, result.panX, "panX");
        assertClose(-80f, result.panY, "panY");
    }

    private static void doubleTapResetsWhenAlreadyZoomedIn() {
        ScreenZoomController.Result result = ScreenZoomController.nextDoubleTapZoom(
            2f,
            -180f,
            -80f,
            180f,
            120f,
            0f,
            40f
        );

        assertClose(1f, result.zoom, "zoom");
        assertClose(0f, result.panX, "panX");
        assertClose(0f, result.panY, "panY");
    }

    private static void assertClose(float expected, float actual, String name) {
        if (Math.abs(expected - actual) > 0.001f) {
            throw new AssertionError(name + " expected " + expected + " but was " + actual);
        }
    }
}
