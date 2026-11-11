package com.gamevideodownloader.service;

import com.gamevideodownloader.client.UpstreamClient;
import com.gamevideodownloader.client.UpstreamException;
import com.gamevideodownloader.config.AppProperties;
import com.gamevideodownloader.download.DownloadJob;
import com.gamevideodownloader.download.ProgressStore;
import com.gamevideodownloader.download.ZipStreamer;
import com.gamevideodownloader.dto.request.DownloadRequest;
import com.gamevideodownloader.dto.request.DownloadRequest.ClipRef;
import com.gamevideodownloader.exception.BadRequestException;
import com.gamevideodownloader.util.FileNamer;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Stream;
import java.util.zip.ZipOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 下载编排：并发下载到临时目录 → 顺序打包 zip 流式回写 → 清理。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DownloadService {

    private final UpstreamClient client;
    private final ProgressStore store;
    private final ZipStreamer zipStreamer;
    private final AppProperties props;

    public void download(DownloadRequest request, HttpServletResponse response) throws IOException {
        List<ClipRef> clips = request.clips();
        if (clips == null || clips.isEmpty()) {
            throw new BadRequestException("未选择任何片段");
        }
        clips.forEach(c -> validateUrl(c.path()));

        String jobId = request.jobId();
        DownloadJob job = new DownloadJob(clips.size());
        store.put(jobId, job);

        Path tempDir = Paths.get(props.download().tempDir(), jobId);
        try {
            Files.createDirectories(tempDir);
            downloadAll(clips, tempDir, job);

            job.markZipPhase();
            response.setContentType("application/zip");
            response.setHeader("Content-Disposition",
                    "attachment; filename=\"" + sanitizeFilename(request.filename()) + "\"");

            int success = 0;
            try (ZipOutputStream zip = new ZipOutputStream(response.getOutputStream())) {
                int index = 1;
                for (int i = 0; i < clips.size(); i++) {
                    Path file = tempDir.resolve(String.valueOf(i));
                    if (!Files.exists(file)) {
                        continue;
                    }
                    ClipRef clip = clips.get(i);
                    String name = FileNamer.clipName(index, clip.jieguo(), clip.jersey(), clip.camname(),
                            clip.keytime());
                    zipStreamer.addEntry(zip, name, file);
                    index++;
                    success++;
                }
            }
            job.finish(success);
        } finally {
            cleanup(tempDir);
        }
    }

    private void downloadAll(List<ClipRef> clips, Path tempDir, DownloadJob job) {
        ExecutorService pool = Executors.newFixedThreadPool(props.download().concurrency());
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < clips.size(); i++) {
                final int idx = i;
                final ClipRef clip = clips.get(i);
                futures.add(pool.submit(() -> downloadOne(idx, clip, tempDir.resolve(String.valueOf(idx)), job)));
            }
            for (Future<?> future : futures) {
                future.get();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new UpstreamException("下载被中断", e);
        } catch (ExecutionException e) {
            throw new UpstreamException("下载失败", e.getCause());
        } finally {
            pool.shutdown();
        }
    }

    private void downloadOne(int idx, ClipRef clip, Path file, DownloadJob job) {
        try (OutputStream out = Files.newOutputStream(file)) {
            long bytes = client.streamMp4(clip.path(), out);
            job.onDownloaded(bytes);
        } catch (Exception e) {
            deleteQuietly(file);
            job.onSkipped(FileNamer.clipName(idx + 1, clip.jieguo(), clip.jersey(), clip.camname(), clip.keytime()),
                    reason(e));
        }
    }

    private void validateUrl(String url) {
        try {
            URI uri = URI.create(url);
            String host = uri.getHost();
            if (host == null || !host.equals(props.upstream().videoHost())) {
                throw new BadRequestException("非法的视频链接主机：" + host);
            }
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("非法的视频链接");
        }
    }

    private String sanitizeFilename(String filename) {
        if (filename == null || filename.isBlank()) {
            return "videos.zip";
        }
        return filename.replaceAll("[\r\n\"]", "_");
    }

    private String reason(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    private void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException ignored) {
            // 忽略单个文件删除失败
        }
    }

    private void cleanup(Path dir) {
        if (dir == null || !Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.deleteIfExists(p);
                } catch (IOException ignored) {
                    // 忽略单个文件删除失败
                }
            });
        } catch (IOException e) {
            log.warn("临时目录清理失败：{}", dir, e);
        }
    }
}
