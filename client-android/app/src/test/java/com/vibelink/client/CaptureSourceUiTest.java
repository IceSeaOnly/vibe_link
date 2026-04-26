package com.vibelink.client;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public final class CaptureSourceUiTest {
    @Test
    public void usesSelectedSourceBeforeDefaultingToMainDisplay() {
        RemoteCaptureSource main = source("display:1", "display", "Built-in Display", null, true);
        RemoteCaptureSource window = source("window:42", "window", "Terminal", "Terminal", false);

        RemoteCaptureSource selected = CaptureSourceUi.INSTANCE.preferredSource(
            new CaptureSourcesInfo(Arrays.asList(main, window), window)
        );

        assertEquals("window:42", selected.getId());
    }

    @Test
    public void fallsBackToMainDisplayWhenSelectedSourceIsMissing() {
        RemoteCaptureSource main = source("display:1", "display", "Built-in Display", null, true);
        RemoteCaptureSource secondary = source("display:2", "display", "Side Display", null, false);

        RemoteCaptureSource selected = CaptureSourceUi.INSTANCE.preferredSource(
            new CaptureSourcesInfo(Arrays.asList(secondary, main), null)
        );

        assertEquals("display:1", selected.getId());
    }

    @Test
    public void derivesDisplayIdOnlyForDisplaySources() {
        assertEquals("69733248", CaptureSourceUi.INSTANCE.displayIdFor(source("display:69733248", "display", "Display", null, true)));
        assertNull(CaptureSourceUi.INSTANCE.displayIdFor(source("window:42", "window", "Terminal", "Terminal", false)));
    }

    @Test
    public void labelsWindowsWithAppNameAndDimensions() {
        String label = CaptureSourceUi.INSTANCE.label(source("window:42", "window", "Terminal - zsh", "Terminal", false));

        assertEquals("Terminal · Terminal - zsh (900x600)", label);
    }

    @Test
    public void separatesWindowPickerTitleAndSubtitleForTouchPicker() {
        RemoteCaptureSource source = source("window:42", "window", "Terminal - zsh", "Terminal", false);

        assertEquals("Terminal - zsh", CaptureSourceUi.INSTANCE.pickerTitle(source));
        assertEquals("Terminal · 900x600", CaptureSourceUi.INSTANCE.pickerSubtitle(source));
        assertEquals("WIN", CaptureSourceUi.INSTANCE.pickerBadge(source));
    }

    @Test
    public void separatesDisplayPickerTitleAndSubtitleForTouchPicker() {
        RemoteCaptureSource source = source("display:1", "display", "Built-in Display", null, true);

        assertEquals("Built-in Display", CaptureSourceUi.INSTANCE.pickerTitle(source));
        assertEquals("900x600", CaptureSourceUi.INSTANCE.pickerSubtitle(source));
        assertEquals("DSP", CaptureSourceUi.INSTANCE.pickerBadge(source));
    }

    private static RemoteCaptureSource source(String id, String type, String name, String appName, boolean main) {
        return new RemoteCaptureSource(id, type, name, appName, 0, 0, 900, 600, 1.0, main);
    }
}
