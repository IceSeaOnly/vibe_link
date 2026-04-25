package com.vibelink.client;

public final class H264FrameBufferTest {
    public static void main(String[] args) throws Exception {
        dropsDeltaFramesUntilKeyFrameArrives();
        clearsBacklogAndWaitsForNextKeyFrameWhenQueueOverflows();
    }

    private static void dropsDeltaFramesUntilKeyFrameArrives() throws Exception {
        H264FrameBuffer buffer = new H264FrameBuffer(3);

        buffer.push(deltaFrame(1));
        assertNull(buffer.poll(), "delta before key");

        byte[] key = keyFrame(2);
        buffer.push(key);

        assertSame(key, buffer.poll(), "first key");
    }

    private static void clearsBacklogAndWaitsForNextKeyFrameWhenQueueOverflows() throws Exception {
        H264FrameBuffer buffer = new H264FrameBuffer(3);
        byte[] oldKey = keyFrame(10);
        byte[] freshKey = keyFrame(20);

        buffer.push(oldKey);
        buffer.push(deltaFrame(11));
        buffer.push(deltaFrame(12));
        buffer.push(deltaFrame(13));
        buffer.push(deltaFrame(14));
        assertSame(oldKey, buffer.poll(), "overflow should keep latest key frame");

        buffer.push(deltaFrame(15));
        assertNull(buffer.poll(), "delta after overflow should wait for fresh key");

        buffer.push(freshKey);
        assertSame(freshKey, buffer.poll(), "fresh key after overflow");
    }

    private static byte[] keyFrame(int marker) {
        return new byte[] {0, 0, 0, 1, 0x67, 1, 0, 0, 0, 1, 0x68, 1, 0, 0, 0, 1, 0x65, (byte) marker};
    }

    private static byte[] deltaFrame(int marker) {
        return new byte[] {0, 0, 0, 1, 0x41, (byte) marker};
    }

    private static void assertSame(byte[] expected, byte[] actual, String name) {
        if (expected != actual) {
            throw new AssertionError(name + " expected same frame");
        }
    }

    private static void assertNull(byte[] actual, String name) {
        if (actual != null) {
            throw new AssertionError(name + " expected null");
        }
    }
}
