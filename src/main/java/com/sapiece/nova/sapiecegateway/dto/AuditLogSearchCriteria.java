package com.sapiece.nova.sapiecegateway.dto;

import lombok.Builder;
import lombok.Value;

import java.time.LocalDateTime;

@Value
@Builder
public class AuditLogSearchCriteria {
    Long userId;
    String userName;
    String module;
    String operation;
    Integer status;
    String clientIp;
    LocalDateTime startTime;
    LocalDateTime endTime;
    int limit;
}
