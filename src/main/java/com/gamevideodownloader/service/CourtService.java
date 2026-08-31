package com.gamevideodownloader.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gamevideodownloader.client.UpstreamClient;
import com.gamevideodownloader.config.AppProperties;
import com.gamevideodownloader.dto.City;
import com.gamevideodownloader.dto.Court;
import com.gamevideodownloader.dto.Page;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** 城市与场馆查询。 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourtService {

    private final UpstreamClient client;
    private final ObjectMapper objectMapper;
    private final AppProperties props;

    public List<City> listCities() {
        JsonNode node = client.postForm(props.upstream().indexUrl(), Map.of("op", "CityList"));
        return objectMapper.convertValue(node, new TypeReference<List<City>>() {
        });
    }

    public Page<Court> listCourts(String keyword, Long cityid, Double lat, Double lon, int page, int size) {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("op", "CourtsList");
        params.put("page", String.valueOf(page));
        params.put("size", String.valueOf(size));
        if (lat != null && lon != null) {
            params.put("lat", String.valueOf(lat));
            params.put("lon", String.valueOf(lon));
        }
        if (cityid != null) {
            params.put("cityid", String.valueOf(cityid));
        }
        if (keyword != null && !keyword.isBlank()) {
            params.put("key", keyword);
        }
        JsonNode node = client.postForm(props.upstream().indexUrl(), params);
        return toPage(node);
    }

    private Page<Court> toPage(JsonNode node) {
        JsonNode rows = node.get("rows");
        List<Court> list = rows == null ? List.of() : objectMapper.convertValue(rows, new TypeReference<List<Court>>() {
        });
        int total = node.path("pager").path("rownum").asInt(list.size());
        return new Page<>(list, total);
    }
}
