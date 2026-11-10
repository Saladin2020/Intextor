package system.core;

/** Plan and execution outcome for a requested goal. */
public final class GoalResult {
    public enum Status { SUCCEEDED, FAILED }

    private final String goalId;
    private final String description;
    private final WorkflowPlan plan;
    private final WorkflowResult workflowResult;

    public GoalResult(String goalId, String description, WorkflowPlan plan, WorkflowResult workflowResult) {
        if (goalId == null || description == null || plan == null || workflowResult == null) {
            throw new IllegalArgumentException("Goal result fields must not be null");
        }
        this.goalId = goalId;
        this.description = description;
        this.plan = plan;
        this.workflowResult = workflowResult;
    }

    public String getGoalId() { return goalId; }
    public String getDescription() { return description; }
    public WorkflowPlan getPlan() { return plan; }
    public WorkflowResult getWorkflowResult() { return workflowResult; }
    public Status getStatus() {
        return workflowResult.isSuccessful() ? Status.SUCCEEDED : Status.FAILED;
    }
    public boolean isSuccessful() { return getStatus() == Status.SUCCEEDED; }
}
