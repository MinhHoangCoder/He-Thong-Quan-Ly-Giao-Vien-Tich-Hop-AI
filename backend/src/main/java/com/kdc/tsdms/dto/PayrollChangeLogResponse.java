package com.kdc.tsdms.dto;

import com.kdc.tsdms.entity.PayrollChangeLog;
import java.math.BigDecimal;
import java.time.Instant;

/** Một dòng lịch sử chốt/mở lại của phiếu lương (GET /api/v1/payroll/{id}/logs). */
public record PayrollChangeLogResponse(
        Long id,
        /** FINALIZE | REOPEN. */
        String action,
        String reason,
        String statusBefore,
        String statusAfter,
        BigDecimal netAmountBefore,
        BigDecimal netAmountAfter,
        Integer changedBy,
        String changedByName,
        Instant changedAt) {

    public static PayrollChangeLogResponse fromEntity(PayrollChangeLog l, String changedByName) {
        return new PayrollChangeLogResponse(
                l.getId(),
                l.getAction(),
                l.getReason(),
                l.getStatusBefore(),
                l.getStatusAfter(),
                l.getNetAmountBefore(),
                l.getNetAmountAfter(),
                l.getChangedBy(),
                changedByName,
                l.getChangedAt());
    }
}
