package com.gamevideodownloader.dto.request;

import java.util.List;

/** 下载请求：前端提交已选片段，后端按序生成 zip 条目名。 */
public record DownloadRequest(String jobId, String filename, List<ClipRef> clips) {

    /** 已选片段的引用，携带生成条目名所需的字段。 */
    public record ClipRef(String path, String jieguo, Integer jersey, String camname, String keytime) {
    }
}
