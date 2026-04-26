package com.vibelink.client;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public final class AppTextCommandNameTest {
    @Test
    public void customAdminCommandNameOverridesBuiltInLocalization() {
        assertEquals(
                "Run Local Checks",
                AppText.commandName("git_status_short", "Run Local Checks", AppLanguage.EN)
        );
        assertEquals(
                "运行本地检查",
                AppText.commandName("git_status_short", "运行本地检查", AppLanguage.ZH)
        );
    }

    @Test
    public void builtInDefaultCommandNameStillLocalizesWhenUnchanged() {
        assertEquals(
                "Git Status",
                AppText.commandName("git_status_short", "Git 状态", AppLanguage.EN)
        );
        assertEquals(
                "Git 状态",
                AppText.commandName("git_status_short", "Git Status", AppLanguage.ZH)
        );
    }
}
