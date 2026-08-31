package com.gamevideodownloader.controller;

import com.gamevideodownloader.dto.City;
import com.gamevideodownloader.dto.Clip;
import com.gamevideodownloader.dto.Court;
import com.gamevideodownloader.dto.Match;
import com.gamevideodownloader.dto.Page;
import com.gamevideodownloader.service.ClipService;
import com.gamevideodownloader.service.CourtService;
import com.gamevideodownloader.service.MatchService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 浏览类接口：城市 / 场馆 / 比赛 / 片段。 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class BrowseController {

    private final CourtService courtService;
    private final MatchService matchService;
    private final ClipService clipService;

    @GetMapping("/cities")
    public List<City> cities() {
        return courtService.listCities();
    }

    @GetMapping("/courts")
    public Page<Court> courts(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long cityid, @RequestParam(required = false) Double lat,
            @RequestParam(required = false) Double lon, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "100") int size) {
        return courtService.listCourts(keyword, cityid, lat, lon, page, size);
    }

    @GetMapping("/courts/{courtid}/matches")
    public Page<Match> matches(@PathVariable long courtid, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "50") int size) {
        return matchService.listMatches(courtid, page, size);
    }

    @GetMapping("/matches/{actid}/clips")
    public List<Clip> clips(@PathVariable long actid) {
        return clipService.listClips(actid);
    }
}
