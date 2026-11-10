package system.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** One module invocation in a workflow and the steps that must finish before it. */
public final class WorkflowStep {
    private final String id;
    private final String moduleName;
    private final List<String> dependencies;
    private final Map<String, Object> inputs;

    public WorkflowStep(String id, String moduleName, List<String> dependencies, Map<String, ?> inputs) {
        if (id == null || id.trim().isEmpty() || moduleName == null || moduleName.trim().isEmpty()) {
            throw new IllegalArgumentException("Workflow step id and module name must not be blank");
        }
        if (dependencies == null || inputs == null) {
            throw new IllegalArgumentException("dependencies and inputs must not be null");
        }
        if (dependencies.contains(id)) {
            throw new IllegalArgumentException("A workflow step cannot depend on itself: " + id);
        }
        Set<String> seenDependencies = new HashSet<String>();
        for (String dependency : dependencies) {
            if (dependency == null || dependency.trim().isEmpty()) {
                throw new IllegalArgumentException("Workflow dependency IDs must not be blank");
            }
            if (!seenDependencies.add(dependency)) {
                throw new IllegalArgumentException("Duplicate workflow dependency: " + dependency);
            }
        }
        this.id = id;
        this.moduleName = moduleName;
        this.dependencies = Collections.unmodifiableList(new ArrayList<String>(dependencies));
        this.inputs = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(inputs));
    }

    public String getId() { return id; }
    public String getModuleName() { return moduleName; }
    public List<String> getDependencies() { return dependencies; }
    public Map<String, Object> getInputs() { return inputs; }
}
