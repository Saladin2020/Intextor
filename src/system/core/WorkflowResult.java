package system.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Ordered status and outputs from a workflow run. */
public final class WorkflowResult {
    public enum Status { SUCCEEDED, FAILED }

    private final String workflowName;
    private final Status status;
    private final String failedStepId;
    private final Map<String, ExecutionResult> stepResults;
    private final Map<String, Object> output;

    public WorkflowResult(String workflowName, Status status, String failedStepId,
            Map<String, ExecutionResult> stepResults, Map<String, Object> output) {
        if (workflowName == null || status == null || stepResults == null || output == null) {
            throw new IllegalArgumentException("Workflow result fields must not be null");
        }
        this.workflowName = workflowName;
        this.status = status;
        this.failedStepId = failedStepId;
        this.stepResults = Collections.unmodifiableMap(new LinkedHashMap<String, ExecutionResult>(stepResults));
        this.output = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(output));
    }

    public String getWorkflowName() { return workflowName; }
    public Status getStatus() { return status; }
    public String getFailedStepId() { return failedStepId; }
    public Map<String, ExecutionResult> getStepResults() { return stepResults; }
    public Map<String, Object> getOutput() { return output; }
    public boolean isSuccessful() { return status == Status.SUCCEEDED; }
}
