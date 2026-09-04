package com.debateai.dto;

import java.util.List;

/**
 * 分页响应
 */
public record PageResponse<T>(List<T> list, long total, long page, long size) {
}
