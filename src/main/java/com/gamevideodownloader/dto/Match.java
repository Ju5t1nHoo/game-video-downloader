package com.gamevideodownloader.dto;

/** 一场 5v5 比赛。 */
public record Match(long actid, long teamid1, long teamid2, String team1, String team2, Integer score1, Integer score2,
        String actdate, String stime, String title, String placename) {
}
