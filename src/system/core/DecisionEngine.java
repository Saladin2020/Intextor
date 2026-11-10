package system.core;

/** Planning strategy seam for deterministic rules or a future AI-backed decision engine. */
public interface DecisionEngine {
    WorkflowPlan plan(Goal goal, ModuleRegistry modules) throws Exception;
}
