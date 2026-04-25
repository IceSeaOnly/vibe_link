package com.vibelink.client;

import java.util.List;

public final class ControlButtonDefaultsTest {
    public static void main(String[] args) {
        defaultsUseKeyboardInsteadOfVoice();
    }

    private static void defaultsUseKeyboardInsteadOfVoice() {
        List<ControlButton> buttons = ControlButtonDefaults.INSTANCE.defaultButtons();

        assertEquals("Keyboard", buttons.get(2).getLabel(), "label");
        assertEquals("keyboard", buttons.get(2).getType(), "type");
    }

    private static void assertEquals(String expected, String actual, String name) {
        if (!expected.equals(actual)) {
            throw new AssertionError(name + " expected " + expected + " but was " + actual);
        }
    }
}
