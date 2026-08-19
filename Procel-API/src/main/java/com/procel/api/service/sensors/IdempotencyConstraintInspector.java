package com.procel.api.service.sensors;

import org.springframework.stereotype.Component;

@Component
public class IdempotencyConstraintInspector {
    static final String DIRECT_IDEMPOTENCY_CONSTRAINT = "ux_metadata_direct_idempotency";
    static final String PROFILE_IDEMPOTENCY_CONSTRAINT = "ux_metadata_profile_idempotency";
    static final String TELEMETRY_RAW_IDEMPOTENCY_CONSTRAINT = "ux_metadata_telemetry_raw_idempotency";

    public IdempotencyConstraint idempotencyConstraint(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            String constraint = constraintName(current);
            if (DIRECT_IDEMPOTENCY_CONSTRAINT.equals(constraint)) {
                return IdempotencyConstraint.DIRECT;
            }
            if (PROFILE_IDEMPOTENCY_CONSTRAINT.equals(constraint)) {
                return IdempotencyConstraint.PROFILE;
            }
            if (TELEMETRY_RAW_IDEMPOTENCY_CONSTRAINT.equals(constraint)) {
                return IdempotencyConstraint.TELEMETRY_RAW;
            }
        }
        return IdempotencyConstraint.NONE;
    }

    private String constraintName(Throwable throwable) {
        try {
            Object serverError = throwable.getClass().getMethod("getServerErrorMessage").invoke(throwable);
            if (serverError == null) {
                return null;
            }
            Object constraint = serverError.getClass().getMethod("getConstraint").invoke(serverError);
            return constraint != null ? constraint.toString() : null;
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }

    public enum IdempotencyConstraint {
        NONE,
        DIRECT,
        PROFILE,
        TELEMETRY_RAW
    }
}
