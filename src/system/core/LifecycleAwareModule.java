package system.core;

/** Optional hooks for modules that need explicit start, pause, resume, and stop work. */
public interface LifecycleAwareModule extends IntextorModule {
    default void onStart() throws Exception { }
    default void onPause() throws Exception { }
    default void onResume() throws Exception { }
    default void onStop() throws Exception { }
}
