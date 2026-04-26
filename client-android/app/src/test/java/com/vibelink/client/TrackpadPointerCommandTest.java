package com.vibelink.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

public final class TrackpadPointerCommandTest {
    @Test
    public void buildsAbsoluteTargetFromVirtualCursor() {
        AbsolutePointerTarget target = TrackpadPointerCommand.INSTANCE.target(320, 180, 640, 360);

        assertNotNull(target);
        assertEquals(320, target.getX());
        assertEquals(180, target.getY());
        assertEquals(640, target.getImageWidth());
        assertEquals(360, target.getImageHeight());
    }

    @Test
    public void rejectsMissingCursor() {
        assertNull(TrackpadPointerCommand.INSTANCE.target(null, 180, 640, 360));
    }
}
