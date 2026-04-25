package com.vibelink.client;

public enum AppLanguage {
    EN,
    ZH;

    public AppLanguage toggle() {
        return this == EN ? ZH : EN;
    }

    public String code() {
        return this == ZH ? "zh" : "en";
    }

    public String switchLabel() {
        return this == ZH ? "中" : "EN";
    }

    public static AppLanguage fromCode(String code) {
        return "zh".equals(code) ? ZH : EN;
    }
}
