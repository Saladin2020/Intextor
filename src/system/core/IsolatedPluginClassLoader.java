package system.core;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;

/** Child-first loader that keeps plugin implementation classes private to its JAR. */
final class IsolatedPluginClassLoader extends URLClassLoader {
    private static final String MODULE_PROVIDER_DESCRIPTOR =
            "META-INF/services/system.core.ModuleProvider";

    IsolatedPluginClassLoader(URL[] urls, ClassLoader parent) {
        super(urls, parent);
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        synchronized (getClassLoadingLock(name)) {
            Class<?> loaded = findLoadedClass(name);
            if (loaded == null) {
                if (PluginDependencyClassLoader.isPlatformOrApiClass(name)) {
                    loaded = getParent().loadClass(name);
                } else {
                    try {
                        loaded = findClass(name);
                    } catch (ClassNotFoundException notInPlugin) {
                        loaded = getParent().loadClass(name);
                    }
                }
            }
            if (resolve) {
                resolveClass(loaded);
            }
            return loaded;
        }
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        if (MODULE_PROVIDER_DESCRIPTOR.equals(name)) {
            return findResources(name);
        }
        return super.getResources(name);
    }

    @Override
    public URL getResource(String name) {
        URL resource = findResource(name);
        return resource != null ? resource : getParent().getResource(name);
    }
}
