package com.gamevideodownloader.controller;

import com.gamevideodownloader.download.DownloadJob;
import com.gamevideodownloader.download.ProgressStore;
import com.gamevideodownloader.dto.ProgressResponse;
import com.gamevideodownloader.dto.request.DownloadRequest;
import com.gamevideodownloader.exception.NotFoundException;
import com.gamevideodownloader.service.DownloadService;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 下载与进度接口。 */
@RestController
@RequestMapping("/api/download")
@RequiredArgsConstructor
public class DownloadController {

    private final DownloadService downloadService;
    private final ProgressStore progressStore;

    @PostMapping
    public void download(@RequestBody DownloadRequest request, HttpServletResponse response) throws IOException {
        downloadService.download(request, response);
    }

    @GetMapping("/{jobId}/progress")
    public ProgressResponse progress(@PathVariable String jobId) {
        DownloadJob job = progressStore.get(jobId);
        if (job == null) {
            throw new NotFoundException("任务不存在或尚未就绪：" + jobId);
        }
        return job.snapshot();
    }
}
