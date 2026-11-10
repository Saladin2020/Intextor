package system.core;

/** Plans and executes structured goals through the registered module catalog. */
public final class AutonomousRuntime {
    private final ExecutionEngine engine;
    private final ModuleRegistry modules;
    private final DecisionEngine decisionEngine;

    public AutonomousRuntime(ExecutionEngine engine, DecisionEngine decisionEngine) {
        if (engine == null || decisionEngine == null) {
            throw new IllegalArgumentException("engine and decisionEngine must not be null");
        }
        this.engine = engine;
        this.modules = engine.moduleRegistry();
        this.decisionEngine = decisionEngine;
    }

    public WorkflowPlan plan(Goal goal) throws Exception {
        return decisionEngine.plan(goal, modules);
    }

    public GoalResult execute(Goal goal) throws Exception {
        if (goal == null) {
            throw new IllegalArgumentException("goal must not be null");
        }
        WorkflowPlan plan = plan(goal);
        WorkflowResult result = engine.executeWorkflow(plan, new ExecutionContext(goal.getInputs()));
        return new GoalResult(goal.getId(), goal.getDescription(), plan, result);
    }
}
