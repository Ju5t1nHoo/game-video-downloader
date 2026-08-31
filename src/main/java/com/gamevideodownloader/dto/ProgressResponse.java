package com.gamevideodownloader.dto;

import java.util.List;

/** 下载进度快照，返回给前端轮询。 */
public record ProgressResponse(String phase, int downloaded, int total, long bytes, int success, List<Skipped> skipped,
        boolean done) {

    public record Skipped(String name, String reason) {
    }
}
