package com.gamevideodownloader.util;

/** 片段免费/付费判定工具。 */
public final class ClipClassifier {

    private ClipClassifier() {
    }

    /**
     * path 含 /bepaid/ 即付费，否则免费。
     *
     * <p>
     * 注意：付费路径内部也含 /vslicing/（如 {@code /bepaid/.../vslicing/...}），故必须优先判 bepaid。
     */
    public static boolean isPaid(String path) {
        return path != null && path.contains("/bepaid/");
    }
}
