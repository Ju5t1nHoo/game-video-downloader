package com.gamevideodownloader.util;

/** 片段文件与 zip 命名工具。 */
public final class FileNamer {

    private FileNamer() {
    }

    /** 片段名：{@code {序号:03d}_{事件}_{机位}_{时间戳}.mp4}。 */
    public static String clipName(int index, String jieguo, String camname, String keytime) {
        String base = String.format("%03d_%s_%s_%s", index, orEmpty(jieguo), camPart(camname), timePart(keytime));
        return sanitize(base) + ".mp4";
    }

    /** zip 名：{@code {主队}vs{客队}_{日期}.zip}，缺队名时回退 {@code match_{日期}.zip}。 */
    public static String zipName(String home, String away, String date) {
        String base = isBlank(home) || isBlank(away)
                ? "match_" + orEmpty(date)
                : home + "vs" + away + "_" + orEmpty(date);
        return sanitize(base) + ".zip";
    }

    /** 机位：{@code 侧位（下）} → {@code 侧位下}。 */
    private static String camPart(String camname) {
        return orEmpty(camname).replace("（", "").replace("）", "").replace("(", "").replace(")", "").replace(" ", "")
                .trim();
    }

    /** 时间戳：{@code 2026-08-27 20:04:35} → {@code 20260827-200435}。 */
    private static String timePart(String keytime) {
        return orEmpty(keytime).replace("-", "").replace(":", "").replace(" ", "-").trim();
    }

    /** 剔除文件名非法字符，替换为下划线。 */
    private static String sanitize(String s) {
        return s.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private static String orEmpty(String s) {
        return s == null ? "" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
