package system.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Immutable status, timing, and output snapshot for one module execution. */
public final class ExecutionResult {
    public enum Status { SUCCEEDED, FAILED }

    private final String moduleName;
    private final String executionId;
    private final String parentExecutionId;
    private final Status status;
    private final long startedAt;
    private final long finishedAt;
    private final Map<String, Object> output;
    private final Throwable error;

    public ExecutionResult(String moduleName, String executionId, String parentExecutionId,
            Status status, long startedAt, long finishedAt, Map<String, Object> output, Throwable error) {
        if (moduleName == null || executionId == null || status == null || output == null) {
            throw new IllegalArgumentException("Execution result fields must not be null");
        }
        this.moduleName = moduleName;
        this.executionId = executionId;
        this.parentExecutionId = parentExecutionId;
        this.status = status;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.output = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(output));
        this.error = error;
    }

    public String getModuleName() { return moduleName; }
    public String getExecutionId() { return executionId; }
    public String getParentExecutionId() { return parentExecutionId; }
    public Status getStatus() { return status; }
    public long getStartedAt() { return startedAt; }
    public long getFinishedAt() { return finishedAt; }
    public long getDurationMillis() { return Math.max(0L, finishedAt - startedAt); }
    public Map<String, Object> getOutput() { return output; }
    public Throwable getError() { return error; }
    public boolean isSuccessful() { return status == Status.SUCCEEDED; }
}
