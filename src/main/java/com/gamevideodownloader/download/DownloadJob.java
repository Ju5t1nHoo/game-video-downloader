package com.gamevideodownloader.download;

import com.gamevideodownloader.dto.ProgressResponse;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** 单次下载任务的可变进度状态，线程安全。 */
public class DownloadJob {

    private final int total;
    private volatile String phase = "download";
    private volatile boolean done = false;
    private final AtomicInteger downloaded = new AtomicInteger();
    private final AtomicInteger success = new AtomicInteger();
    private final AtomicLong bytes = new AtomicLong();
    private final ConcurrentLinkedQueue<ProgressResponse.Skipped> skipped = new ConcurrentLinkedQueue<>();

    public DownloadJob(int total) {
        this.total = total;
    }

    /** 一个片段下载成功。 */
    public void onDownloaded(long fileBytes) {
        downloaded.incrementAndGet();
        bytes.addAndGet(fileBytes);
    }

    /** 一个片段被跳过。 */
    public void onSkipped(String name, String reason) {
        downloaded.incrementAndGet();
        skipped.add(new ProgressResponse.Skipped(name, reason));
    }

    public void markZipPhase() {
        this.phase = "zip";
    }

    public void finish(int successCount) {
        this.success.set(successCount);
        this.done = true;
    }

    public ProgressResponse snapshot() {
        return new ProgressResponse(phase, downloaded.get(), total, bytes.get(), success.get(), List.copyOf(skipped),
                done);
    }
}
