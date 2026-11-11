package com.gamevideodownloader.dto;

/** 一个视频片段（= 一个 mp4，由 PlayByPlay 的事件平铺而来）。 */
public record Clip(long notid, String keytime, String offensive, String jieguo, long teamid, long starid,
        Integer jersey, String camname, String path, String poster, boolean paid) {
}
