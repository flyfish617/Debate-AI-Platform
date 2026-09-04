package com.debateai.dto;

import com.debateai.entity.Report;

import java.time.LocalDateTime;

/**
 * 举报响应
 */
public record ReportResponse(
        Long id,
        Long reporterId,
        String targetType,
        Long targetId,
        String reason,
        String status,
        LocalDateTime createdAt
) {

    public static ReportResponse from(Report report) {
        return new ReportResponse(
                report.getId(),
                report.getReporterId(),
                report.getTargetType(),
                report.getTargetId(),
                report.getReason(),
                report.getStatus(),
                report.getCreatedAt()
        );
    }
}
