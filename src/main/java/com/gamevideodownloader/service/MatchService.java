package com.gamevideodownloader.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamevideodownloader.client.UpstreamClient;
import com.gamevideodownloader.config.AppProperties;
import com.gamevideodownloader.dto.Match;
import com.gamevideodownloader.dto.Page;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 比赛列表查询。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MatchService {

    private final UpstreamClient client;
    private final ObjectMapper objectMapper;
    private final AppProperties props;

    public Page<Match> listMatches(long courtid, int page, int size) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("op", "List5v5");
        params.put("courtid", String.valueOf(courtid));
        params.put("page", String.valueOf(page));
        params.put("size", String.valueOf(size));
        JsonNode node = client.postForm(props.upstream().activityUrl(), params);
        JsonNode rows = node.get("rows");
        List<Match> list = rows == null ? List.of() : objectMapper.convertValue(rows, new TypeReference<List<Match>>() {
        });
        int total = node.path("pager").path("rownum").asInt(list.size());
        return new Page<>(list, total);
    }
}
