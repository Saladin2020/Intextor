package system.core;

import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

/** Runs registered modules synchronously, asynchronously, in parallel, or later. */
public final class ExecutionEngine implements AutoCloseable, ModuleInvoker {
    private static final Logger LOGGER = Logger.getLogger(ExecutionEngine.class.getName());
    private final ModuleRegistry registry;
    private final ModuleEventBus events;
    private final ModuleLifecycleManager lifecycle;
    private final PluginManager plugins;
    private final ExecutorService workers;
    private final ScheduledExecutorService scheduler;
    private final Map<String, Set<ScheduledTask>> scheduledTasks = new HashMap<String, Set<ScheduledTask>>();
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public ExecutionEngine(ModuleRegistry registry) {
        this(registry, new ModuleEventBus(),
                Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors())),
                Executors.newScheduledThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors())));
    }

    public ExecutionEngine(ModuleRegistry registry, ModuleEventBus events,
            ExecutorService workers, ScheduledExecutorService scheduler) {
        if (registry == null || events == null || workers == null || scheduler == null) {
            throw new IllegalArgumentException("Engine dependencies must not be null");
        }
        this.registry = registry;
        this.events = events;
        this.lifecycle = new ModuleLifecycleManager(registry);
        this.plugins = new PluginManager(registry, lifecycle);
        this.workers = workers;
        this.scheduler = scheduler;
    }

    public ModuleEventBus getEventBus() {
        return events;
    }

    public ModuleLifecycleManager.State getModuleState(String moduleName) {
        ensureOpen();
        return lifecycle.getState(moduleName);
    }

    public void startModule(String moduleName) throws Exception {
        ensureOpen();
        lifecycle.start(moduleName);
    }

    public void pauseModule(String moduleName) throws Exception {
        ensureOpen();
        cancelScheduled(moduleName);
        lifecycle.pause(moduleName);
        cancelScheduled(moduleName);
    }

    public void resumeModule(String moduleName) throws Exception {
        ensureOpen();
        lifecycle.resume(moduleName);
    }

    public void stopModule(String moduleName) throws Exception {
        ensureOpen();
        cancelScheduled(moduleName);
        lifecycle.stop(moduleName);
        cancelScheduled(moduleName);
    }

    public void restartModule(String moduleName) throws Exception {
        ensureOpen();
        cancelScheduled(moduleName);
        lifecycle.restart(moduleName);
        cancelScheduled(moduleName);
    }

    public void execute(String moduleName, ExecutionContext context) throws Exception {
        ExecutionResult result = runModule(moduleName, context);
        if (!result.isSuccessful()) {
            Throwable error = result.getError();
            if (error instanceof Exception) {
                throw (Exception) error;
            }
            if (error instanceof Error) {
                throw (Error) error;
            }
            throw new IllegalStateException("Module execution failed: " + moduleName);
        }
    }

    @Override
    public ExecutionResult invoke(String moduleName, ExecutionContext context) {
        return runModule(moduleName, context);
    }

    private ExecutionResult runModule(String moduleName, ExecutionContext context) {
        ensureOpen();
        if (moduleName == null || moduleName.trim().isEmpty()) {
            throw new IllegalArgumentException("Module name must not be blank");
        }
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        long startedAt = System.currentTimeMillis();
        IntextorModule module;
        try {
            module = lifecycle.beginExecution(moduleName);
        } catch (Exception exception) {
            events.publish(new ModuleEvent(ModuleEvent.Type.FAILED, moduleName, context.getExecutionId(), exception));
            return failedResult(moduleName, context, startedAt, exception);
        } catch (Error error) {
            events.publish(new ModuleEvent(ModuleEvent.Type.FAILED, moduleName, context.getExecutionId(), error));
            return failedResult(moduleName, context, startedAt, error);
        }
        ModuleInvoker previousInvoker = context.bindInvoker(this);
        try {
            events.publish(new ModuleEvent(ModuleEvent.Type.STARTED, moduleName, context.getExecutionId(), null));
            module.execute(context);
            events.publish(new ModuleEvent(ModuleEvent.Type.SUCCEEDED, moduleName, context.getExecutionId(), null));
            return new ExecutionResult(moduleName, context.getExecutionId(), context.getParentExecutionId(),
                    ExecutionResult.Status.SUCCEEDED, startedAt, System.currentTimeMillis(), context.snapshot(), null);
        } catch (Exception exception) {
            events.publish(new ModuleEvent(ModuleEvent.Type.FAILED, moduleName, context.getExecutionId(), exception));
            return failedResult(moduleName, context, startedAt, exception);
        } catch (Error error) {
            events.publish(new ModuleEvent(ModuleEvent.Type.FAILED, moduleName, context.getExecutionId(), error));
            return failedResult(moduleName, context, startedAt, error);
        } finally {
            context.restoreInvoker(previousInvoker);
            lifecycle.endExecution(moduleName);
        }
    }

    private static ExecutionResult failedResult(String moduleName, ExecutionContext context,
            long startedAt, Throwable error) {
        return new ExecutionResult(moduleName, context.getExecutionId(), context.getParentExecutionId(),
                ExecutionResult.Status.FAILED, startedAt, System.currentTimeMillis(), context.snapshot(), error);
    }

    public Future<?> submit(final String moduleName, final ExecutionContext context) {
        ensureOpen();
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        registry.get(moduleName);
        return workers.submit(new Runnable() {
            @Override
            public void run() {
                try {
                    execute(moduleName, context);
                } catch (Exception exception) {
                    throw new RuntimeException("Module execution failed: " + moduleName, exception);
                }
            }
        });
    }

    /** Starts every requested module at once; all executions share the supplied context. */
    public List<Future<?>> executeParallel(List<String> moduleNames, ExecutionContext context) {
        ensureOpen();
        if (moduleNames == null || context == null) {
            throw new IllegalArgumentException("moduleNames and context must not be null");
        }
        List<Future<?>> futures = new ArrayList<Future<?>>();
        for (String moduleName : moduleNames) {
            futures.add(submit(moduleName, context));
        }
        return Collections.unmodifiableList(futures);
    }

    /** Runs workflow steps in dependency order and exposes dependency outputs to each step. */
    public WorkflowResult executeWorkflow(WorkflowPlan plan, ExecutionContext context) {
        ensureOpen();
        if (plan == null || context == null) {
            throw new IllegalArgumentException("plan and context must not be null");
        }
        List<WorkflowStep> orderedSteps = topologicalOrder(plan);
        Map<String, Object> baseInputs = context.snapshot();
        Map<String, ExecutionResult> stepResults = new LinkedHashMap<String, ExecutionResult>();

        for (WorkflowStep step : orderedSteps) {
            Map<String, Object> stepInputs = new LinkedHashMap<String, Object>(baseInputs);
            for (String dependencyId : step.getDependencies()) {
                stepInputs.putAll(stepResults.get(dependencyId).getOutput());
            }
            stepInputs.putAll(step.getInputs());
            ExecutionContext stepContext = context.child(stepInputs);
            ExecutionResult result = invoke(step.getModuleName(), stepContext);
            stepResults.put(step.getId(), result);
            if (!result.isSuccessful()) {
                return new WorkflowResult(plan.getName(), WorkflowResult.Status.FAILED,
                        step.getId(), stepResults, context.snapshot());
            }
            context.merge(result.getOutput());
        }
        return new WorkflowResult(plan.getName(), WorkflowResult.Status.SUCCEEDED,
                null, stepResults, context.snapshot());
    }

    private static List<WorkflowStep> topologicalOrder(WorkflowPlan plan) {
        Map<String, WorkflowStep> byId = plan.getStepsById();
        Set<String> placed = new LinkedHashSet<String>();
        List<WorkflowStep> ordered = new ArrayList<WorkflowStep>();
        while (ordered.size() < byId.size()) {
            WorkflowStep next = null;
            for (WorkflowStep step : byId.values()) {
                if (placed.contains(step.getId())) {
                    continue;
                }
                boolean ready = true;
                for (String dependency : step.getDependencies()) {
                    if (!byId.containsKey(dependency)) {
                        throw new IllegalArgumentException("Unknown workflow dependency '" + dependency
                                + "' for step '" + step.getId() + "'");
                    }
                    if (!placed.contains(dependency)) {
                        ready = false;
                        break;
                    }
                }
                if (ready) {
                    next = step;
                    break;
                }
            }
            if (next == null) {
                throw new IllegalArgumentException("Workflow contains a dependency cycle: " + plan.getName());
            }
            placed.add(next.getId());
            ordered.add(next);
        }
        return ordered;
    }

    public synchronized ScheduledFuture<?> schedule(String moduleName, ExecutionContext context,
            long delay, TimeUnit unit) {
        if (unit == null || delay < 0) {
            throw new IllegalArgumentException("A non-negative delay and time unit are required");
        }
        return scheduleTask(moduleName, context, false, new SchedulerAction() {
            @Override
            public ScheduledFuture<?> schedule(Runnable task) {
                return scheduler.schedule(task, delay, unit);
            }
        });
    }

    public ScheduledFuture<?> scheduleAtFixedRate(String moduleName, ExecutionContext context,
            long initialDelay, long period, TimeUnit unit) {
        if (unit == null || initialDelay < 0 || period <= 0) {
            throw new IllegalArgumentException("Initial delay must be non-negative and period must be positive");
        }
        return scheduleTask(moduleName, context, true, new SchedulerAction() {
            @Override
            public ScheduledFuture<?> schedule(Runnable task) {
                return scheduler.scheduleAtFixedRate(task, initialDelay, period, unit);
            }
        });
    }

    public ScheduledFuture<?> scheduleWithFixedDelay(String moduleName, ExecutionContext context,
            long initialDelay, long delay, TimeUnit unit) {
        if (unit == null || initialDelay < 0 || delay <= 0) {
            throw new IllegalArgumentException("Initial delay must be non-negative and delay must be positive");
        }
        return scheduleTask(moduleName, context, true, new SchedulerAction() {
            @Override
            public ScheduledFuture<?> schedule(Runnable task) {
                return scheduler.scheduleWithFixedDelay(task, initialDelay, delay, unit);
            }
        });
    }

    /** Registers ServiceLoader modules from a caller-owned plugin class loader. */
    public int loadPlugins(String pluginId, ClassLoader classLoader) {
        ensureOpen();
        return plugins.load(pluginId, classLoader);
    }

    public int loadPlugins(String pluginId, ClassLoader classLoader, List<String> dependencies) {
        ensureOpen();
        return plugins.load(pluginId, classLoader, dependencies);
    }

    /** Loads a plugin JAR in an isolated class loader owned and closed by the engine. */
    public int loadPluginJar(String pluginId, URL jarUrl) {
        return loadPluginJar(pluginId, jarUrl, Collections.<String>emptyList(),
                ExecutionEngine.class.getClassLoader());
    }

    public int loadPluginJar(String pluginId, URL jarUrl, ClassLoader parent) {
        return loadPluginJar(pluginId, jarUrl, Collections.<String>emptyList(), parent);
    }

    public int loadPluginJar(String pluginId, URL jarUrl, List<String> dependencies) {
        return loadPluginJar(pluginId, jarUrl, dependencies, ExecutionEngine.class.getClassLoader());
    }

    public int loadPluginJar(String pluginId, URL jarUrl, List<String> dependencies, ClassLoader parent) {
        ensureOpen();
        return plugins.loadJar(pluginId, jarUrl, dependencies, parent);
    }

    public void unloadPlugin(String pluginId) throws Exception {
        ensureOpen();
        List<String> moduleNames = plugins.moduleNames(pluginId);
        for (String moduleName : moduleNames) {
            cancelScheduled(moduleName);
        }
        plugins.unload(pluginId);
        for (String moduleName : moduleNames) {
            cancelScheduled(moduleName);
        }
    }

    public List<String> getLoadedPluginIds() {
        ensureOpen();
        return Collections.unmodifiableList(plugins.loadedPluginIds());
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            synchronized (this) {
                scheduler.shutdownNow();
                scheduledTasks.clear();
            }
            workers.shutdownNow();
            try {
                lifecycle.stopAll();
            } catch (Exception exception) {
                LOGGER.log(Level.WARNING, "One or more modules failed to stop while closing the engine", exception);
            }
            try {
                plugins.closeAll();
            } catch (Exception exception) {
                LOGGER.log(Level.WARNING, "One or more plugins failed to unload while closing the engine", exception);
            }
        }
    }

    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("ExecutionEngine is closed");
        }
    }

    private synchronized void cancelScheduled(String moduleName) {
        Set<ScheduledTask> tasks = scheduledTasks.remove(moduleName);
        if (tasks != null) {
            for (ScheduledTask task : tasks) {
                task.future.cancel(false);
            }
        }
    }

    private synchronized void removeScheduled(ScheduledTask task) {
        Set<ScheduledTask> tasks = scheduledTasks.get(task.moduleName);
        if (tasks != null) {
            tasks.remove(task);
            if (tasks.isEmpty()) {
                scheduledTasks.remove(task.moduleName);
            }
        }
    }

    private synchronized ScheduledFuture<?> scheduleTask(String moduleName, ExecutionContext context,
            boolean repeating, SchedulerAction schedulerAction) {
        ensureOpen();
        if (context == null) {
            throw new IllegalArgumentException("context must not be null");
        }
        registry.get(moduleName);
        ScheduledTask task = new ScheduledTask(moduleName, context, repeating);
        TrackedScheduledFuture future = new TrackedScheduledFuture(task, schedulerAction.schedule(task));
        task.future = future;
        Set<ScheduledTask> tasks = scheduledTasks.get(moduleName);
        if (tasks == null) {
            tasks = new HashSet<ScheduledTask>();
            scheduledTasks.put(moduleName, tasks);
        }
        tasks.add(task);
        try {
            if (!lifecycle.isSchedulingAllowed(moduleName)) {
                throw new IllegalStateException("Module is paused or stopped: " + moduleName);
            }
        } catch (RuntimeException exception) {
            future.cancel(false);
            throw exception;
        }
        return future;
    }

    private interface SchedulerAction {
        ScheduledFuture<?> schedule(Runnable task);
    }

    private final class ScheduledTask implements Runnable {
        private final String moduleName;
        private final ExecutionContext context;
        private final boolean repeating;
        private TrackedScheduledFuture future;

        private ScheduledTask(String moduleName, ExecutionContext context, boolean repeating) {
            this.moduleName = moduleName;
            this.context = context;
            this.repeating = repeating;
        }

        @Override
        public void run() {
            if (!repeating) {
                removeScheduled(this);
            }
            try {
                execute(moduleName, context);
            } catch (Exception exception) {
                removeScheduled(this);
                throw new RuntimeException("Scheduled module execution failed: " + moduleName, exception);
            } catch (Error error) {
                removeScheduled(this);
                throw error;
            }
        }
    }

    private final class TrackedScheduledFuture implements ScheduledFuture<Object> {
        private final ScheduledTask task;
        private final ScheduledFuture<?> delegate;

        private TrackedScheduledFuture(ScheduledTask task, ScheduledFuture<?> delegate) {
            this.task = task;
            this.delegate = delegate;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return delegate.getDelay(unit);
        }

        @Override
        public int compareTo(Delayed other) {
            return delegate.compareTo(other);
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = delegate.cancel(mayInterruptIfRunning);
            removeScheduled(task);
            return cancelled;
        }

        @Override
        public boolean isCancelled() { return delegate.isCancelled(); }

        @Override
        public boolean isDone() { return delegate.isDone(); }

        @Override
        public Object get() throws InterruptedException, ExecutionException { return delegate.get(); }

        @Override
        public Object get(long timeout, TimeUnit unit)
                throws InterruptedException, ExecutionException, TimeoutException {
            return delegate.get(timeout, unit);
        }
    }
}
