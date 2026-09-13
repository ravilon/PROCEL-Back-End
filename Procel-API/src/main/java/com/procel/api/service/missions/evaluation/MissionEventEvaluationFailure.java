package com.procel.api.service.missions.evaluation;

public class MissionEventEvaluationFailure extends RuntimeException {
    private final boolean permanent;

    public MissionEventEvaluationFailure(String message, boolean permanent) {
        super(message);
        this.permanent = permanent;
    }

    public MissionEventEvaluationFailure(String message, boolean permanent, Throwable cause) {
        super(message, cause);
        this.permanent = permanent;
    }

    public boolean permanent() {
        return permanent;
    }

    public static MissionEventEvaluationFailure permanent(String message, Throwable cause) {
        return new MissionEventEvaluationFailure(message, true, cause);
    }

    public static MissionEventEvaluationFailure transientFailure(String message, Throwable cause) {
        return new MissionEventEvaluationFailure(message, false, cause);
    }
}
