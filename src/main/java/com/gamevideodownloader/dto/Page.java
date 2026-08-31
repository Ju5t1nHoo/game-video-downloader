package com.gamevideodownloader.dto;

import java.util.List;

/** 分页结果。 */
public record Page<T>(List<T> rows, int total) {
}
