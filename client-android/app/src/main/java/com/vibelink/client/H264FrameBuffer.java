package com.vibelink.client;

import java.util.ArrayDeque;

public final class H264FrameBuffer {
    private final int maxFrames;
    private final ArrayDeque<byte[]> frames = new ArrayDeque<>();
    private byte[] latestKeyFrame = null;
    private boolean needsKeyFrame = true;
    private boolean closed = false;

    public H264FrameBuffer(int maxFrames) {
        this.maxFrames = Math.max(1, maxFrames);
    }

    public synchronized void push(byte[] payload) {
        if (closed || payload == null || payload.length == 0) return;
        boolean keyFrame = isKeyFrame(payload);
        if (keyFrame) {
            latestKeyFrame = payload;
            frames.clear();
            needsKeyFrame = false;
            frames.addLast(payload);
            notifyAll();
            return;
        }
        if (needsKeyFrame) return;
        if (frames.size() >= maxFrames) {
            frames.clear();
            if (latestKeyFrame != null) {
                frames.addLast(latestKeyFrame);
                needsKeyFrame = true;
                notifyAll();
            } else {
                needsKeyFrame = true;
            }
            return;
        }
        frames.addLast(payload);
        notifyAll();
    }

    public synchronized byte[] take() throws InterruptedException {
        while (!closed && frames.isEmpty()) {
            wait();
        }
        return frames.pollFirst();
    }

    public synchronized byte[] poll() {
        return frames.pollFirst();
    }

    public synchronized void close() {
        closed = true;
        frames.clear();
        latestKeyFrame = null;
        notifyAll();
    }

    public static boolean isKeyFrame(byte[] payload) {
        for (int index = 0; index + 4 < payload.length; index++) {
            int startCodeLength = 0;
            if (payload[index] == 0 && payload[index + 1] == 0 && payload[index + 2] == 1) {
                startCodeLength = 3;
            } else if (index + 5 < payload.length && payload[index] == 0 && payload[index + 1] == 0 && payload[index + 2] == 0 && payload[index + 3] == 1) {
                startCodeLength = 4;
            }
            if (startCodeLength > 0) {
                int nalIndex = index + startCodeLength;
                if (nalIndex < payload.length) {
                    int nalType = payload[nalIndex] & 0x1f;
                    if (nalType == 5) return true;
                }
                index = nalIndex;
            }
        }
        return false;
    }
}
