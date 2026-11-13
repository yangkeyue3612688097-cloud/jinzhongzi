package org.example.backend.util;

import org.example.backend.entity.OperationLog;
import org.example.backend.mapper.OperationLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Records key business actions so the platform can show an audit trail for
 * evidence, agreements and settlements.
 */
@Component
public class OperationLogger {

    private static final Logger log = LoggerFactory.getLogger(OperationLogger.class);

    @Autowired
    private OperationLogMapper operationLogMapper;

    public void record(Long userId, String operation, String detail) {
        try {
            OperationLog entry = new OperationLog();
            entry.setUserId(userId);
            entry.setOperation(operation);
            entry.setDetail(detail);
            entry.setIpAddress(null);
            operationLogMapper.insert(entry);
        } catch (Exception e) {
            log.warn("Failed to record operation audit log: op={}", operation, e);
        }
    }
}
