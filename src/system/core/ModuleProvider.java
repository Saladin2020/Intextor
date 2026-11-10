package system.core;

/** ServiceLoader extension point for plugins that contribute modules. */
public interface ModuleProvider {
    Iterable<? extends IntextorModule> getModules();
}
