package system.core;

import java.util.Collection;

/** Optional metadata used by planners to discover modules by capability. */
public interface CapabilityAwareModule extends IntextorModule {
    Collection<String> getCapabilities();
}
