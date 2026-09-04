package com.debateai.dto;

import com.debateai.entity.Report;

import java.time.LocalDateTime;

/**
 * 管理员举报处理响应
 */
public record AdminReportResponse(
        Long id,
        Long reporterId,
        String reporterUsername,
        String targetType,
        Long targetId,
        String targetLabel,
        String targetStatus,
        String reason,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static AdminReportResponse from(
            Report report,
            String reporterUsername,
            String targetLabel,
            String targetStatus
    ) {
        return new AdminReportResponse(
                report.getId(),
                report.getReporterId(),
                reporterUsername,
                report.getTargetType(),
                report.getTargetId(),
                targetLabel,
                targetStatus,
                report.getReason(),
                report.getStatus(),
                report.getCreatedAt(),
                report.getUpdatedAt()
        );
    }
}
