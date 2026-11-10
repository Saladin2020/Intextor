package system.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Ordered workflow definition with explicit step dependencies. */
public final class WorkflowPlan {
    private final String name;
    private final Map<String, WorkflowStep> steps = new LinkedHashMap<String, WorkflowStep>();

    public WorkflowPlan(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Workflow name must not be blank");
        }
        this.name = name;
    }

    public synchronized WorkflowPlan addStep(String id, String moduleName) {
        return addStep(id, moduleName, Collections.<String>emptyList(), Collections.<String, Object>emptyMap());
    }

    public synchronized WorkflowPlan addStep(String id, String moduleName,
            List<String> dependencies, Map<String, ?> inputs) {
        WorkflowStep step = new WorkflowStep(id, moduleName, dependencies, inputs);
        if (steps.containsKey(id)) {
            throw new IllegalArgumentException("Workflow step id is already used: " + id);
        }
        steps.put(id, step);
        return this;
    }

    public String getName() { return name; }

    public synchronized List<WorkflowStep> getSteps() {
        return Collections.unmodifiableList(new ArrayList<WorkflowStep>(steps.values()));
    }

    public synchronized Map<String, WorkflowStep> getStepsById() {
        return Collections.unmodifiableMap(new LinkedHashMap<String, WorkflowStep>(steps));
    }
}
