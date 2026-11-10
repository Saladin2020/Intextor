package system.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** A structured request for the runtime to fulfill using module capabilities. */
public final class Goal {
    private final String id;
    private final String description;
    private final List<String> requiredCapabilities;
    private final Map<String, Object> inputs;
    private final Map<String, String> preferredModules;

    public Goal(String description, List<String> requiredCapabilities, Map<String, ?> inputs) {
        this(UUID.randomUUID().toString(), description, requiredCapabilities, inputs,
                Collections.<String, String>emptyMap());
    }

    public Goal(String description, List<String> requiredCapabilities,
            Map<String, ?> inputs, Map<String, String> preferredModules) {
        this(UUID.randomUUID().toString(), description, requiredCapabilities, inputs, preferredModules);
    }

    public Goal(String id, String description, List<String> requiredCapabilities,
            Map<String, ?> inputs, Map<String, String> preferredModules) {
        if (id == null || id.trim().isEmpty() || description == null || description.trim().isEmpty()) {
            throw new IllegalArgumentException("Goal id and description must not be blank");
        }
        if (requiredCapabilities == null || inputs == null || preferredModules == null) {
            throw new IllegalArgumentException("Goal capabilities, inputs, and preferences must not be null");
        }
        if (requiredCapabilities.isEmpty()) {
            throw new IllegalArgumentException("A goal must require at least one capability");
        }
        Set<String> seen = new HashSet<String>();
        for (String capability : requiredCapabilities) {
            if (capability == null || capability.trim().isEmpty()) {
                throw new IllegalArgumentException("Goal capability names must not be blank");
            }
            if (!seen.add(capability)) {
                throw new IllegalArgumentException("Duplicate goal capability: " + capability);
            }
        }
        for (Map.Entry<String, String> preference : preferredModules.entrySet()) {
            if (!seen.contains(preference.getKey())) {
                throw new IllegalArgumentException("Preferred module declared for an unrequested capability: "
                        + preference.getKey());
            }
            if (preference.getValue() == null || preference.getValue().trim().isEmpty()) {
                throw new IllegalArgumentException("Preferred module names must not be blank");
            }
        }
        this.id = id;
        this.description = description;
        this.requiredCapabilities = Collections.unmodifiableList(
                new ArrayList<String>(requiredCapabilities));
        this.inputs = Collections.unmodifiableMap(new LinkedHashMap<String, Object>(inputs));
        this.preferredModules = Collections.unmodifiableMap(new LinkedHashMap<String, String>(preferredModules));
    }

    public String getId() { return id; }
    public String getDescription() { return description; }
    public List<String> getRequiredCapabilities() { return requiredCapabilities; }
    public Map<String, Object> getInputs() { return inputs; }
    public Map<String, String> getPreferredModules() { return preferredModules; }
}
