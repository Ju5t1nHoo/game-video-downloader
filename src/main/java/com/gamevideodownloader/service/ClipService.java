package com.gamevideodownloader.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.gamevideodownloader.client.UpstreamClient;
import com.gamevideodownloader.config.AppProperties;
import com.gamevideodownloader.dto.Clip;
import com.gamevideodownloader.util.ClipClassifier;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 拉全单场比赛的视频片段（翻页聚合 PlayByPlay）。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ClipService {

    private static final int PAGE_SIZE = 200;

    private final UpstreamClient client;
    private final AppProperties props;

    public List<Clip> listClips(long actid) {
        List<Clip> result = new ArrayList<>();
        int page = 1;
        while (page <= props.download().maxPages() && result.size() < props.download().maxEvents()) {
            JsonNode node = client.postForm(props.upstream().activityUrl(), params(actid, page));
            JsonNode rows = node.get("rows");
            if (rows == null || !rows.isArray() || rows.isEmpty()) {
                break;
            }
            for (JsonNode event : rows) {
                flatten(event, result);
            }
            int pagenum = node.path("pager").path("pagenum").asInt(1);
            if (page >= pagenum) {
                break;
            }
            page++;
        }
        return result;
    }

    private Map<String, String> params(long actid, int page) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("op", "PlayByPlay");
        params.put("actid", String.valueOf(actid));
        params.put("getvideo", "1");
        params.put("size", String.valueOf(PAGE_SIZE));
        params.put("page", String.valueOf(page));
        return params;
    }

    private void flatten(JsonNode event, List<Clip> result) {
        JsonNode videos = event.get("videos");
        if (videos == null || !videos.isArray()) {
            return;
        }
        long notid = event.path("notid").asLong();
        String keytime = event.path("keytime").asText(null);
        String offensive = event.path("offensive").asText(null);
        String jieguo = event.path("jieguo").asText(null);
        long teamid = event.path("teamid").asLong();
        long starid = event.path("starid").asLong();
        for (JsonNode video : videos) {
            String path = video.path("path").asText(null);
            if (path == null || path.isBlank()) {
                continue;
            }
            String camname = video.path("camname").asText(null);
            String poster = video.path("poster").asText(null);
            result.add(new Clip(notid, keytime, offensive, jieguo, teamid, starid, camname, path, poster,
                    ClipClassifier.isPaid(path)));
        }
    }
}
