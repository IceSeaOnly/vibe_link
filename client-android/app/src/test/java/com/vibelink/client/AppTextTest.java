package com.vibelink.client;

public final class AppTextTest {
    public static void main(String[] args) {
        returnsEnglishUiTextByDefault();
        returnsChineseUiTextWhenSelected();
        localizesBuiltInControlLabels();
        localizesBuiltInQuickTextAndCommandNames();
    }

    private static void returnsEnglishUiTextByDefault() {
        assertEquals("VibeLink", AppText.text(AppText.Key.BRAND, AppLanguage.EN), "brand");
        assertEquals("Connect", AppText.text(AppText.Key.CONNECT, AppLanguage.EN), "connect");
        assertEquals("Text to send", AppText.text(AppText.Key.TEXT_TO_SEND, AppLanguage.EN), "text hint");
        assertEquals("Find", AppText.text(AppText.Key.FIND_SERVER, AppLanguage.EN), "find");
        assertEquals("Copyright © Hangzhou Duomo Technology Co., Ltd. All rights reserved.", AppText.text(AppText.Key.COPYRIGHT, AppLanguage.EN), "copyright");
    }

    private static void returnsChineseUiTextWhenSelected() {
        assertEquals("鹊桥", AppText.text(AppText.Key.BRAND, AppLanguage.ZH), "brand");
        assertEquals("连接", AppText.text(AppText.Key.CONNECT, AppLanguage.ZH), "connect");
        assertEquals("待发送文本", AppText.text(AppText.Key.TEXT_TO_SEND, AppLanguage.ZH), "text hint");
        assertEquals("发现", AppText.text(AppText.Key.FIND_SERVER, AppLanguage.ZH), "find");
        assertEquals("版权所有 © 杭州多模科技有限公司", AppText.text(AppText.Key.COPYRIGHT, AppLanguage.ZH), "copyright");
    }

    private static void localizesBuiltInControlLabels() {
        assertEquals("Keyboard", AppText.controlLabel("keyboard", "Keyboard", AppLanguage.EN), "keyboard en");
        assertEquals("键盘", AppText.controlLabel("keyboard", "Keyboard", AppLanguage.ZH), "keyboard zh");
    }

    private static void localizesBuiltInQuickTextAndCommandNames() {
        assertEquals("Continue Fix", AppText.quickTextName("continue_fix", "继续修复", AppLanguage.EN), "quick en");
        assertEquals("继续修复", AppText.quickTextName("continue_fix", "Continue Fix", AppLanguage.ZH), "quick zh");
        assertEquals("Git Status", AppText.commandName("git_status_short", "Git 状态", AppLanguage.EN), "command en");
        assertEquals("Git 状态", AppText.commandName("git_status_short", "Git Status", AppLanguage.ZH), "command zh");
    }

    private static void assertEquals(String expected, String actual, String name) {
        if (!expected.equals(actual)) {
            throw new AssertionError(name + " expected " + expected + " but was " + actual);
        }
    }
}
