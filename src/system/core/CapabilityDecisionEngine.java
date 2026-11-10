package system.core;

import java.util.Collections;
import java.util.List;

/** Deterministic planner that maps each requested capability to one registered module. */
public final class CapabilityDecisionEngine implements DecisionEngine {
    @Override
    public WorkflowPlan plan(Goal goal, ModuleRegistry modules) {
        if (goal == null || modules == null) {
            throw new IllegalArgumentException("goal and modules must not be null");
        }
        WorkflowPlan plan = new WorkflowPlan("goal-" + goal.getId());
        String previousStep = null;
        int index = 1;
        for (String capability : goal.getRequiredCapabilities()) {
            List<IntextorModule> candidates = modules.findByCapability(capability);
            String selected = selectModule(goal, capability, candidates);
            String stepId = "step-" + index++;
            List<String> dependencies = previousStep == null
                    ? Collections.<String>emptyList() : Collections.singletonList(previousStep);
            plan.addStep(stepId, selected, dependencies, Collections.<String, Object>emptyMap());
            previousStep = stepId;
        }
        return plan;
    }

    private static String selectModule(Goal goal, String capability, List<IntextorModule> candidates) {
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("No module provides required capability: " + capability);
        }
        String preferred = goal.getPreferredModules().get(capability);
        if (preferred != null) {
            for (IntextorModule candidate : candidates) {
                if (candidate.getName().equals(preferred)) {
                    return preferred;
                }
            }
            throw new IllegalArgumentException("Preferred module '" + preferred
                    + "' does not provide capability '" + capability + "'");
        }
        if (candidates.size() > 1) {
            throw new IllegalArgumentException("Capability '" + capability
                    + "' has multiple providers; specify a preferred module: " + moduleNames(candidates));
        }
        return candidates.get(0).getName();
    }

    private static String moduleNames(List<IntextorModule> modules) {
        StringBuilder names = new StringBuilder();
        for (IntextorModule module : modules) {
            if (names.length() > 0) {
                names.append(", ");
            }
            names.append(module.getName());
        }
        return names.toString();
    }
}
