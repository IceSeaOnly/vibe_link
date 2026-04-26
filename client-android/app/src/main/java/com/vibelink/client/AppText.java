package com.vibelink.client;

import java.util.HashMap;
import java.util.Map;

public final class AppText {
    public enum Key {
        BRAND,
        CONNECT,
        CONNECTING,
        DISCONNECT,
        AUTH,
        FIND_SERVER,
        SCAN_PAIRING,
        TOKEN,
        FRAME,
        NO_FRAME,
        NOT_CONNECTED,
        DISPLAY,
        DEFAULT_STREAM,
        CAPTURE_SOURCE,
        DEFAULT_SOURCE,
        SCROLL_UP,
        SCROLL_DOWN,
        TEXT_TO_SEND,
        SUBMIT_WITH_ENTER,
        QUICK_TEXTS,
        COMMANDS,
        COMMAND_OUTPUT,
        SHORTCUT_BUTTONS,
        ADD_SHORTCUT,
        TEXT_EMPTY,
        ENTER_SERVER_AND_TOKEN,
        CONNECTING_STATUS,
        CONNECTED,
        CONNECT_FAILED,
        DISCONNECTED,
        HEALTH_DISCONNECTED,
        STREAM_ENDED,
        STREAM_ERROR,
        ACTIONS_LOAD_FAILED,
        NOT_CONNECTED_ERROR,
        STARTING_COMMAND,
        COMMAND_FAILED,
        COMMAND_STATUS,
        EXIT,
        SHORTCUT_SENT,
        SHORTCUT_FAILED,
        CONNECT_BEFORE_SHORTCUT,
        NO_SCREEN_FRAME,
        DRAG_POINTER,
        SHORTCUT_NAME,
        SHORTCUT,
        NAME_SHORTCUT,
        CANCEL,
        SAVE,
        SHORTCUT_NAME_EMPTY,
        SHORTCUT_SAVED,
        SHORTCUT_SAVE_FAILED,
        SENT,
        SEND_FAILED,
        SCREEN_VIEW_MODE,
        POINTER_CLICK_MODE,
        TRACKPAD_MODE,
        SWITCH_INTERACTION_MODE,
        KEYBOARD_OPEN,
        KEYBOARD_CLOSED,
        COPYRIGHT,
        DISCOVERING,
        DISCOVERY_FAILED,
        SERVER_FOUND,
        PAIRING_APPLIED,
        PAIRING_INVALID
    }

    private static final Map<Key, String> EN = new HashMap<>();
    private static final Map<Key, String> ZH = new HashMap<>();
    private static final Map<String, String> CONTROL_EN = new HashMap<>();
    private static final Map<String, String> CONTROL_ZH = new HashMap<>();
    private static final Map<String, String> QUICK_EN = new HashMap<>();
    private static final Map<String, String> QUICK_ZH = new HashMap<>();
    private static final Map<String, String> COMMAND_EN = new HashMap<>();
    private static final Map<String, String> COMMAND_ZH = new HashMap<>();
    private static final Map<String, String> SHORTCUT_EN = new HashMap<>();
    private static final Map<String, String> SHORTCUT_ZH = new HashMap<>();

    static {
        put(Key.BRAND, "VibeLink", "鹊桥");
        put(Key.CONNECT, "Connect", "连接");
        put(Key.CONNECTING, "Connecting...", "连接中...");
        put(Key.DISCONNECT, "Disconnect", "断开");
        put(Key.AUTH, "Auth", "认证");
        put(Key.FIND_SERVER, "Find", "发现");
        put(Key.SCAN_PAIRING, "Scan", "扫码");
        put(Key.TOKEN, "Token", "令牌");
        put(Key.FRAME, "Frame", "画面");
        put(Key.NO_FRAME, "No frame", "无画面");
        put(Key.NOT_CONNECTED, "Not connected", "未连接");
        put(Key.DISPLAY, "Display", "显示器");
        put(Key.DEFAULT_STREAM, "Default stream", "默认画面");
        put(Key.CAPTURE_SOURCE, "Source", "串流源");
        put(Key.DEFAULT_SOURCE, "Default source", "默认串流源");
        put(Key.SCROLL_UP, "Scroll Up", "向上滚动");
        put(Key.SCROLL_DOWN, "Scroll Down", "向下滚动");
        put(Key.TEXT_TO_SEND, "Text to send", "待发送文本");
        put(Key.SUBMIT_WITH_ENTER, "Submit with Enter", "发送后回车");
        put(Key.QUICK_TEXTS, "Quick Texts", "快捷文本");
        put(Key.COMMANDS, "Commands", "命令");
        put(Key.COMMAND_OUTPUT, "Command output", "命令输出");
        put(Key.SHORTCUT_BUTTONS, "Shortcut Buttons", "快捷按钮");
        put(Key.ADD_SHORTCUT, "Add Shortcut", "添加快捷按钮");
        put(Key.TEXT_EMPTY, "Text is empty", "文本为空");
        put(Key.ENTER_SERVER_AND_TOKEN, "Enter server address and token", "请输入服务地址和令牌");
        put(Key.CONNECTING_STATUS, "Connecting...", "连接中...");
        put(Key.CONNECTED, "Connected", "已连接");
        put(Key.CONNECT_FAILED, "Connect failed", "连接失败");
        put(Key.DISCONNECTED, "Disconnected", "已断开");
        put(Key.HEALTH_DISCONNECTED, "Disconnected: health check failed", "已断开：健康检查失败");
        put(Key.STREAM_ENDED, "Disconnected: stream ended", "已断开：画面流结束");
        put(Key.STREAM_ERROR, "Disconnected: stream error", "已断开：画面流错误");
        put(Key.ACTIONS_LOAD_FAILED, "Actions load failed", "操作加载失败");
        put(Key.NOT_CONNECTED_ERROR, "Not connected", "未连接");
        put(Key.STARTING_COMMAND, "Starting command...", "正在启动命令...");
        put(Key.COMMAND_FAILED, "Command failed", "命令失败");
        put(Key.COMMAND_STATUS, "Status", "状态");
        put(Key.EXIT, "Exit", "退出码");
        put(Key.SHORTCUT_SENT, "Shortcut sent", "快捷按钮已发送");
        put(Key.SHORTCUT_FAILED, "Shortcut failed", "快捷按钮失败");
        put(Key.CONNECT_BEFORE_SHORTCUT, "Connect before adding shortcut", "请先连接再添加快捷按钮");
        put(Key.NO_SCREEN_FRAME, "No screen frame yet", "还没有屏幕画面");
        put(Key.DRAG_POINTER, "Drag the blue pointer to the target, then release", "将蓝色指针拖到目标位置后松开");
        put(Key.SHORTCUT_NAME, "Shortcut name", "快捷按钮名称");
        put(Key.SHORTCUT, "Shortcut", "快捷按钮");
        put(Key.NAME_SHORTCUT, "Name shortcut", "命名快捷按钮");
        put(Key.CANCEL, "Cancel", "取消");
        put(Key.SAVE, "Save", "保存");
        put(Key.SHORTCUT_NAME_EMPTY, "Shortcut name is empty", "快捷按钮名称为空");
        put(Key.SHORTCUT_SAVED, "Shortcut saved", "快捷按钮已保存");
        put(Key.SHORTCUT_SAVE_FAILED, "Shortcut save failed", "快捷按钮保存失败");
        put(Key.SENT, "Sent", "已发送");
        put(Key.SEND_FAILED, "Send failed", "发送失败");
        put(Key.SCREEN_VIEW_MODE, "Screen view mode", "屏幕模式");
        put(Key.POINTER_CLICK_MODE, "Pointer click mode", "指针模式");
        put(Key.TRACKPAD_MODE, "Trackpad mode", "触摸板模式");
        put(Key.SWITCH_INTERACTION_MODE, "Switch interaction mode", "切换交互模式");
        put(Key.KEYBOARD_OPEN, "Keyboard open", "键盘已打开");
        put(Key.KEYBOARD_CLOSED, "Keyboard closed", "键盘已关闭");
        put(Key.COPYRIGHT, "Copyright © Hangzhou Duomo Technology Co., Ltd. All rights reserved.", "版权所有 © 杭州多模科技有限公司");
        put(Key.DISCOVERING, "Discovering VibeLink server...", "正在发现 VibeLink 服务...");
        put(Key.DISCOVERY_FAILED, "No VibeLink server found", "未发现 VibeLink 服务");
        put(Key.SERVER_FOUND, "Server found", "已发现服务");
        put(Key.PAIRING_APPLIED, "Pairing applied", "已填入配对信息");
        put(Key.PAIRING_INVALID, "Invalid pairing QR", "配对二维码无效");

        putControl("sendText", "Send Text", "发送文本");
        putControl("backspace", "Backspace", "退格");
        putControl("keyboard", "Keyboard", "键盘");
        putControl("voice", "Keyboard", "键盘");
        putControl("selectAll", "Select All", "全选");
        putControl("enter", "Ent", "回车");
        putControl("cmdEnter", "Cmd+Ent", "命令回车");
        putControl("copy", "Copy", "复制");
        putControl("paste", "Paste", "粘贴");
        putControl("escape", "ESC", "退出");
        putControl("interrupt", "Break", "中断");
        putControl("undo", "Undo", "撤销");
        putControl("close", "Close", "关闭");

        putQuick("continue_fix", "Continue Fix", "继续修复");
        putQuick("run_related_tests", "Run Related Tests", "运行相关测试");
        putQuick("summarize_changes", "Summarize Changes", "总结当前修改");

        putCommand("pwd", "Print Directory", "显示当前目录");
        putCommand("git_status_short", "Git Status", "Git 状态");
        putCommand("ls", "List Files", "列出文件");

        putShortcut("center_click", "Center Click", "点击屏幕中央");
    }

    private AppText() {
    }

    public static String text(Key key, AppLanguage language) {
        return table(language).get(key);
    }

    public static String controlLabel(String type, String fallback, AppLanguage language) {
        return named(CONTROL_EN, CONTROL_ZH, type, fallback, language);
    }

    public static String quickTextName(String id, String fallback, AppLanguage language) {
        return named(QUICK_EN, QUICK_ZH, id, fallback, language);
    }

    public static String commandName(String id, String fallback, AppLanguage language) {
        return namedOnlyWhenFallbackIsBuiltIn(COMMAND_EN, COMMAND_ZH, id, fallback, language);
    }

    public static String shortcutName(String id, String fallback, AppLanguage language) {
        return named(SHORTCUT_EN, SHORTCUT_ZH, id, fallback, language);
    }

    private static String named(Map<String, String> en, Map<String, String> zh, String key, String fallback, AppLanguage language) {
        String value = language == AppLanguage.ZH ? zh.get(key) : en.get(key);
        return value == null || value.isEmpty() ? fallback : value;
    }

    private static String namedOnlyWhenFallbackIsBuiltIn(Map<String, String> en, Map<String, String> zh, String key, String fallback, AppLanguage language) {
        String english = en.get(key);
        String chinese = zh.get(key);
        boolean isBuiltInName = fallback == null || fallback.isEmpty() || fallback.equals(english) || fallback.equals(chinese);
        if (!isBuiltInName) {
            return fallback;
        }
        return named(en, zh, key, fallback, language);
    }

    private static Map<Key, String> table(AppLanguage language) {
        return language == AppLanguage.ZH ? ZH : EN;
    }

    private static void put(Key key, String en, String zh) {
        EN.put(key, en);
        ZH.put(key, zh);
    }

    private static void putControl(String type, String en, String zh) {
        CONTROL_EN.put(type, en);
        CONTROL_ZH.put(type, zh);
    }

    private static void putQuick(String id, String en, String zh) {
        QUICK_EN.put(id, en);
        QUICK_ZH.put(id, zh);
    }

    private static void putCommand(String id, String en, String zh) {
        COMMAND_EN.put(id, en);
        COMMAND_ZH.put(id, zh);
    }

    private static void putShortcut(String id, String en, String zh) {
        SHORTCUT_EN.put(id, en);
        SHORTCUT_ZH.put(id, zh);
    }
}
