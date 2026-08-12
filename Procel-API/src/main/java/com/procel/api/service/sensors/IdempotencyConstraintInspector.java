package com.procel.api.service.sensors;

import org.springframework.stereotype.Component;

@Component
public class IdempotencyConstraintInspector {
    static final String IDEMPOTENCY_CONSTRAINT = "ux_medicao_ingestao_producer_sensor_message";

    public boolean isIdempotencyUniqueViolation(Throwable ex) {
        for (Throwable current = ex; current != null; current = current.getCause()) {
            String constraint = constraintName(current);
            if (IDEMPOTENCY_CONSTRAINT.equals(constraint)) {
                return true;
            }
        }
        return false;
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
}
