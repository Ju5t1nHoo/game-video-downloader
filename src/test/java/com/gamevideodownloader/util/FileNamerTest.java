package com.gamevideodownloader.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class FileNamerTest {

    @Test
    void clipNameSanitizesCamnameAndTime() {
        String name = FileNamer.clipName(1, "miss", "侧位（下）", "2026-08-27 20:04:35");
        assertEquals("001_miss_侧位下_20260827-200435.mp4", name);
    }

    @Test
    void clipNameZeroPadsIndex() {
        String name = FileNamer.clipName(12, "goal", "正位", "2026-08-27 20:04:35");
        assertEquals("012_goal_正位_20260827-200435.mp4", name);
    }

    @Test
    void zipNameUsesTeams() {
        assertEquals("米兰春天vs白鹤社区_2026-08-27.zip", FileNamer.zipName("米兰春天", "白鹤社区", "2026-08-27"));
    }

    @Test
    void zipNameFallsBackWithoutTeams() {
        assertEquals("match_2026-08-27.zip", FileNamer.zipName(null, "", "2026-08-27"));
    }
}
