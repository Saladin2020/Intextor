package system.core;

import java.io.Closeable;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.ServiceLoader;

/** Loads and unloads ServiceLoader plugins while tracking their registered modules. */
public final class PluginManager {
    private final ModuleRegistry registry;
    private final ModuleLifecycleManager lifecycle;
    private final Map<String, LoadedPlugin> plugins = new LinkedHashMap<String, LoadedPlugin>();

    public PluginManager(ModuleRegistry registry, ModuleLifecycleManager lifecycle) {
        if (registry == null || lifecycle == null) {
            throw new IllegalArgumentException("registry and lifecycle must not be null");
        }
        this.registry = registry;
        this.lifecycle = lifecycle;
    }

    /** Loads a plugin using a caller-owned class loader, which is not closed on unload. */
    public synchronized int load(String pluginId, ClassLoader classLoader) {
        return load(pluginId, classLoader, Collections.<String>emptyList(), false);
    }

    /** Loads a caller-owned class loader and records its already-loaded plugin dependencies. */
    public synchronized int load(String pluginId, ClassLoader classLoader, List<String> dependencies) {
        return load(pluginId, classLoader, dependencies, false);
    }

    /** Creates an isolated loader for one plugin JAR and takes ownership of that loader. */
    public synchronized int loadJar(String pluginId, URL jarUrl, ClassLoader parent) {
        return loadJar(pluginId, jarUrl, Collections.<String>emptyList(), parent);
    }

    /** Loads a JAR with visibility to the named plugins, which must already be loaded. */
    public synchronized int loadJar(String pluginId, URL jarUrl, List<String> dependencies, ClassLoader parent) {
        if (jarUrl == null || parent == null) {
            throw new IllegalArgumentException("jarUrl and parent must not be null");
        }
        List<LoadedPlugin> dependencyPlugins = resolveDependencies(pluginId, dependencies);
        List<ClassLoader> dependencyLoaders = new ArrayList<ClassLoader>();
        for (LoadedPlugin dependency : dependencyPlugins) {
            dependencyLoaders.add(dependency.classLoader);
        }
        PluginDependencyClassLoader dependencyParent =
                new PluginDependencyClassLoader(parent, dependencyLoaders);
        URLClassLoader loader = new IsolatedPluginClassLoader(new URL[] { jarUrl }, dependencyParent);
        try {
            return load(pluginId, loader, dependencies, true);
        } catch (RuntimeException exception) {
            closeQuietly(loader);
            throw exception;
        } catch (Error error) {
            closeQuietly(loader);
            throw error;
        }
    }

    public synchronized void unload(String pluginId) throws Exception {
        requirePluginId(pluginId);
        LoadedPlugin plugin = plugins.get(pluginId);
        if (plugin == null) {
            throw new IllegalArgumentException("No plugin is loaded with id '" + pluginId + "'");
        }
        List<String> dependents = new ArrayList<String>();
        for (Map.Entry<String, LoadedPlugin> entry : plugins.entrySet()) {
            if (entry.getValue().dependencies.contains(pluginId)) {
                dependents.add(entry.getKey());
            }
        }
        if (!dependents.isEmpty()) {
            throw new IllegalStateException("Unload dependent plugins first: " + dependents);
        }

        // stop() drains active module executions before module instances are removed.
        for (String moduleName : plugin.moduleNames) {
            lifecycle.stop(moduleName);
        }
        for (String moduleName : plugin.moduleNames) {
            registry.unregister(moduleName);
            lifecycle.forget(moduleName);
        }
        plugins.remove(pluginId);
        if (plugin.closeLoader && plugin.classLoader instanceof Closeable) {
            ((Closeable) plugin.classLoader).close();
        }
    }

    public synchronized List<String> loadedPluginIds() {
        return new ArrayList<String>(plugins.keySet());
    }

    synchronized List<String> moduleNames(String pluginId) {
        requirePluginId(pluginId);
        LoadedPlugin plugin = plugins.get(pluginId);
        if (plugin == null) {
            throw new IllegalArgumentException("No plugin is loaded with id '" + pluginId + "'");
        }
        return new ArrayList<String>(plugin.moduleNames);
    }

    public synchronized void closeAll() throws Exception {
        Exception failure = null;
        List<String> pluginIds = new ArrayList<String>(plugins.keySet());
        Collections.reverse(pluginIds);
        for (String pluginId : pluginIds) {
            try {
                unload(pluginId);
            } catch (Exception exception) {
                if (failure == null) {
                    failure = new Exception("One or more plugins failed to unload");
                }
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private int load(String pluginId, ClassLoader classLoader, List<String> dependencies, boolean closeLoader) {
        requirePluginId(pluginId);
        if (classLoader == null) {
            throw new IllegalArgumentException("classLoader must not be null");
        }
        if (plugins.containsKey(pluginId)) {
            throw new IllegalArgumentException("Plugin id is already loaded: " + pluginId);
        }
        resolveDependencies(pluginId, dependencies);

        List<IntextorModule> modules = new ArrayList<IntextorModule>();
        for (ModuleProvider provider : ServiceLoader.load(ModuleProvider.class, classLoader)) {
            Iterable<? extends IntextorModule> provided = provider.getModules();
            if (provided == null) {
                throw new IllegalStateException("ModuleProvider returned null: " + provider.getClass().getName());
            }
            for (IntextorModule module : provided) {
                if (module == null) {
                    throw new IllegalStateException("ModuleProvider returned a null module: " + provider.getClass().getName());
                }
                modules.add(module);
            }
        }

        List<String> registeredNames = new ArrayList<String>();
        try {
            for (IntextorModule module : modules) {
                String moduleName = module.getName();
                registry.register(module);
                registeredNames.add(moduleName);
            }
        } catch (RuntimeException exception) {
            rollback(registeredNames);
            throw exception;
        } catch (Error error) {
            rollback(registeredNames);
            throw error;
        }
        plugins.put(pluginId, new LoadedPlugin(classLoader, registeredNames,
                new ArrayList<String>(dependencies), closeLoader));
        return registeredNames.size();
    }

    private List<LoadedPlugin> resolveDependencies(String pluginId, List<String> dependencies) {
        requirePluginId(pluginId);
        if (dependencies == null) {
            throw new IllegalArgumentException("dependencies must not be null");
        }
        List<LoadedPlugin> resolved = new ArrayList<LoadedPlugin>();
        List<String> seen = new ArrayList<String>();
        for (String dependencyId : dependencies) {
            requirePluginId(dependencyId);
            if (pluginId.equals(dependencyId)) {
                throw new IllegalArgumentException("A plugin cannot depend on itself: " + pluginId);
            }
            if (seen.contains(dependencyId)) {
                throw new IllegalArgumentException("Duplicate plugin dependency: " + dependencyId);
            }
            LoadedPlugin dependency = plugins.get(dependencyId);
            if (dependency == null) {
                throw new IllegalArgumentException("Plugin dependency is not loaded: " + dependencyId);
            }
            seen.add(dependencyId);
            resolved.add(dependency);
        }
        return resolved;
    }

    private void rollback(List<String> moduleNames) {
        for (String moduleName : moduleNames) {
            registry.unregister(moduleName);
            lifecycle.forget(moduleName);
        }
    }

    private static void requirePluginId(String pluginId) {
        if (pluginId == null || pluginId.trim().isEmpty()) {
            throw new IllegalArgumentException("Plugin id must not be blank");
        }
    }

    private static void closeQuietly(Closeable closeable) {
        try {
            closeable.close();
        } catch (IOException ignored) {
            // Preserve the plugin loading failure; the loader will be released with its class references.
        }
    }

    private static final class LoadedPlugin {
        private final ClassLoader classLoader;
        private final List<String> moduleNames;
        private final List<String> dependencies;
        private final boolean closeLoader;

        private LoadedPlugin(ClassLoader classLoader, List<String> moduleNames,
                List<String> dependencies, boolean closeLoader) {
            this.classLoader = classLoader;
            this.moduleNames = new ArrayList<String>(moduleNames);
            this.dependencies = new ArrayList<String>(dependencies);
            this.closeLoader = closeLoader;
        }
    }
}
