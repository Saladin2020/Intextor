package system.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Thread-safe registry of modules available to the execution engine. */
public final class ModuleRegistry {
    private final ConcurrentHashMap<String, IntextorModule> modules =
            new ConcurrentHashMap<String, IntextorModule>();

    public void register(IntextorModule module) {
        if (module == null) {
            throw new IllegalArgumentException("module must not be null");
        }
        String name = module.getName();
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Module name must not be blank");
        }
        IntextorModule existing = modules.putIfAbsent(name, module);
        if (existing != null) {
            throw new IllegalArgumentException("A module named '" + name + "' is already registered");
        }
    }

    public IntextorModule get(String name) {
        requireName(name);
        IntextorModule module = modules.get(name);
        if (module == null) {
            throw new IllegalArgumentException("No module registered with name '" + name + "'");
        }
        return module;
    }

    public IntextorModule unregister(String name) {
        requireName(name);
        return modules.remove(name);
    }

    public List<IntextorModule> findByCapability(String capability) {
        if (capability == null || capability.trim().isEmpty()) {
            throw new IllegalArgumentException("Capability must not be blank");
        }
        List<IntextorModule> matches = new ArrayList<IntextorModule>();
        for (IntextorModule module : modules.values()) {
            if (!(module instanceof CapabilityAwareModule)) {
                continue;
            }
            Collection<String> capabilities = ((CapabilityAwareModule) module).getCapabilities();
            if (capabilities != null && capabilities.contains(capability)) {
                matches.add(module);
            }
        }
        Collections.sort(matches, (left, right) -> left.getName().compareTo(right.getName()));
        return Collections.unmodifiableList(matches);
    }

    private static void requireName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Module name must not be blank");
        }
    }

    public List<String> names() {
        List<String> names = new ArrayList<String>(modules.keySet());
        Collections.sort(names);
        return Collections.unmodifiableList(names);
    }

    public Map<String, IntextorModule> snapshot() {
        return Collections.unmodifiableMap(new ConcurrentHashMap<String, IntextorModule>(modules));
    }
}
