package system.core;

import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Vector;

/** Resolves plugin dependencies before falling back to the application class path. */
final class PluginDependencyClassLoader extends ClassLoader {
    private final List<ClassLoader> dependencies;

    PluginDependencyClassLoader(ClassLoader parent, List<ClassLoader> dependencies) {
        super(parent);
        this.dependencies = new ArrayList<ClassLoader>(dependencies);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (isPlatformOrApiClass(name)) {
            return super.loadClass(name, false);
        }
        for (ClassLoader dependency : dependencies) {
            try {
                return dependency.loadClass(name);
            } catch (ClassNotFoundException ignored) {
                // Try the next declared dependency.
            }
        }
        return super.loadClass(name, false);
    }

    @Override
    public URL getResource(String name) {
        for (ClassLoader dependency : dependencies) {
            URL resource = dependency.getResource(name);
            if (resource != null) {
                return resource;
            }
        }
        return super.getResource(name);
    }

    @Override
    public Enumeration<URL> getResources(String name) throws java.io.IOException {
        Vector<URL> resources = new Vector<URL>();
        for (ClassLoader dependency : dependencies) {
            Enumeration<URL> found = dependency.getResources(name);
            while (found.hasMoreElements()) {
                URL resource = found.nextElement();
                if (!resources.contains(resource)) {
                    resources.add(resource);
                }
            }
        }
        Enumeration<URL> parentResources = super.getResources(name);
        while (parentResources.hasMoreElements()) {
            URL resource = parentResources.nextElement();
            if (!resources.contains(resource)) {
                resources.add(resource);
            }
        }
        return resources.elements();
    }

    static boolean isPlatformOrApiClass(String name) {
        return name.startsWith("java.") || name.startsWith("javax.")
                || name.startsWith("jdk.") || name.startsWith("sun.")
                || name.startsWith("com.sun.") || name.startsWith("system.core.");
    }
}
