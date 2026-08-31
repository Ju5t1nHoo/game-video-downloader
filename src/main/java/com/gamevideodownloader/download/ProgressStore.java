package com.gamevideodownloader.download;

import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** jobId → 下载任务的内存存储（单实例，重启即失）。 */
@Component
public class ProgressStore {

    private final ConcurrentHashMap<String, DownloadJob> jobs = new ConcurrentHashMap<>();

    public void put(String jobId, DownloadJob job) {
        jobs.put(jobId, job);
    }

    public DownloadJob get(String jobId) {
        return jobs.get(jobId);
    }
}
