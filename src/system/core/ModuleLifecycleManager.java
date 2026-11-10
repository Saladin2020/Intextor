package system.core;

import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Coordinates module lifecycle transitions and drains active executions before pause or stop. */
public final class ModuleLifecycleManager {
    public enum State { NEW, STARTING, RUNNING, PAUSING, PAUSED, RESUMING, STOPPING, STOPPED }

    private final ModuleRegistry registry;
    private final Map<String, Entry> entries = new LinkedHashMap<String, Entry>();
    private final ThreadLocal<List<String>> executingModules = new ThreadLocal<List<String>>() {
        @Override
        protected List<String> initialValue() {
            return new ArrayList<String>();
        }
    };

    public ModuleLifecycleManager(ModuleRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        this.registry = registry;
    }

    public synchronized State getState(String moduleName) {
        return entry(moduleName).state;
    }

    public synchronized void start(String moduleName) throws Exception {
        Entry entry = awaitStable(moduleName);
        if (entry.state == State.RUNNING) {
            return;
        }
        if (entry.state == State.PAUSED) {
            throw new IllegalStateException("Module '" + moduleName + "' is paused; resume it instead");
        }
        State previous = entry.state;
        entry.state = State.STARTING;
        entry.transitionOwner = Thread.currentThread();
        try {
            invokeStart(entry.module);
            entry.state = State.RUNNING;
        } catch (Exception exception) {
            entry.state = previous;
            throw exception;
        } catch (Error error) {
            entry.state = previous;
            throw error;
        } finally {
            entry.transitionOwner = null;
            notifyAll();
        }
    }

    public synchronized void pause(String moduleName) throws Exception {
        rejectSelfWait(moduleName, "pause");
        Entry entry = awaitStable(moduleName);
        if (entry.state != State.RUNNING) {
            throw new IllegalStateException("Only a running module can be paused: " + moduleName);
        }
        entry.state = State.PAUSING;
        entry.transitionOwner = Thread.currentThread();
        waitForIdle(entry, State.RUNNING);
        try {
            if (entry.module instanceof LifecycleAwareModule) {
                ((LifecycleAwareModule) entry.module).onPause();
            }
            entry.state = State.PAUSED;
        } catch (Exception exception) {
            entry.state = State.RUNNING;
            throw exception;
        } catch (Error error) {
            entry.state = State.RUNNING;
            throw error;
        } finally {
            entry.transitionOwner = null;
            notifyAll();
        }
    }

    public synchronized void resume(String moduleName) throws Exception {
        Entry entry = awaitStable(moduleName);
        if (entry.state != State.PAUSED) {
            throw new IllegalStateException("Only a paused module can be resumed: " + moduleName);
        }
        entry.state = State.RESUMING;
        entry.transitionOwner = Thread.currentThread();
        try {
            if (entry.module instanceof LifecycleAwareModule) {
                ((LifecycleAwareModule) entry.module).onResume();
            }
            entry.state = State.RUNNING;
        } catch (Exception exception) {
            entry.state = State.PAUSED;
            throw exception;
        } catch (Error error) {
            entry.state = State.PAUSED;
            throw error;
        } finally {
            entry.transitionOwner = null;
            notifyAll();
        }
    }

    public synchronized void stop(String moduleName) throws Exception {
        rejectSelfWait(moduleName, "stop");
        Entry entry = awaitStable(moduleName);
        if (entry.state == State.STOPPED) {
            return;
        }
        State previous = entry.state;
        entry.state = State.STOPPING;
        entry.transitionOwner = Thread.currentThread();
        waitForIdle(entry, previous);
        try {
            if (previous != State.NEW && entry.module instanceof LifecycleAwareModule) {
                ((LifecycleAwareModule) entry.module).onStop();
            }
            entry.state = State.STOPPED;
        } catch (Exception exception) {
            entry.state = previous;
            throw exception;
        } catch (Error error) {
            entry.state = previous;
            throw error;
        } finally {
            entry.transitionOwner = null;
            notifyAll();
        }
    }

    public synchronized void restart(String moduleName) throws Exception {
        Entry entry = awaitStable(moduleName);
        if (entry.state != State.NEW) {
            stop(moduleName);
        }
        start(moduleName);
    }

    synchronized IntextorModule beginExecution(String moduleName) throws Exception {
        Entry entry = entry(moduleName);
        if (isTransitioning(entry.state)) {
            throw new IllegalStateException("Module '" + moduleName + "' is changing lifecycle state");
        }
        if (entry.state == State.NEW) {
            entry.state = State.STARTING;
            entry.transitionOwner = Thread.currentThread();
            try {
                invokeStart(entry.module);
                entry.state = State.RUNNING;
            } catch (Exception exception) {
                entry.state = State.NEW;
                notifyAll();
                throw exception;
            } catch (Error error) {
                entry.state = State.NEW;
                notifyAll();
                throw error;
            } finally {
                entry.transitionOwner = null;
                notifyAll();
            }
        }
        if (entry.state != State.RUNNING) {
            throw new IllegalStateException("Module '" + moduleName + "' is "
                    + entry.state.name().toLowerCase() + "; start or resume it before execution");
        }
        entry.activeExecutions++;
        executingModules.get().add(moduleName);
        return entry.module;
    }

    synchronized void endExecution(String moduleName) {
        Entry entry = entries.get(moduleName);
        if (entry != null && entry.activeExecutions > 0) {
            entry.activeExecutions--;
            notifyAll();
        }
        List<String> stack = executingModules.get();
        if (!stack.isEmpty()) {
            stack.remove(stack.size() - 1);
        }
        if (stack.isEmpty()) {
            executingModules.remove();
        }
    }

    synchronized boolean isSchedulingAllowed(String moduleName) {
        Entry entry = entry(moduleName);
        return entry.state == State.NEW || entry.state == State.RUNNING;
    }

    public synchronized void forget(String moduleName) {
        Entry entry = entries.get(moduleName);
        if (entry == null) {
            return;
        }
        if (entry.activeExecutions != 0 || entry.state == State.RUNNING
                || entry.state == State.STARTING || entry.state == State.PAUSED
                || entry.state == State.PAUSING || entry.state == State.RESUMING
                || entry.state == State.STOPPING) {
            throw new IllegalStateException("Stop module before removing it: " + moduleName);
        }
        entries.remove(moduleName);
    }

    public synchronized void stopAll() throws Exception {
        Exception failure = null;
        for (String name : registry.names()) {
            try {
                stop(name);
            } catch (Exception exception) {
                if (failure == null) {
                    failure = new Exception("One or more modules failed to stop");
                }
                failure.addSuppressed(exception);
            }
        }
        if (failure != null) {
            throw failure;
        }
    }

    private Entry entry(String moduleName) {
        IntextorModule module = registry.get(moduleName);
        Entry entry = entries.get(moduleName);
        if (entry == null) {
            entry = new Entry(module);
            entries.put(moduleName, entry);
        } else if (entry.module != module) {
            throw new IllegalStateException("Registry module changed without lifecycle cleanup: " + moduleName);
        }
        return entry;
    }

    private Entry awaitStable(String moduleName) throws InterruptedException {
        Entry entry = entry(moduleName);
        while (isTransitioning(entry.state)) {
            if (entry.transitionOwner == Thread.currentThread()) {
                throw new IllegalStateException("Lifecycle hook cannot wait on its own transition: " + moduleName);
            }
            try {
                wait();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw exception;
            }
            entry = entry(moduleName);
        }
        return entry;
    }

    private void waitForIdle(Entry entry, State rollbackState) throws InterruptedException {
        try {
            while (entry.activeExecutions > 0) {
                wait();
            }
        } catch (InterruptedException exception) {
            entry.state = rollbackState;
            entry.transitionOwner = null;
            notifyAll();
            Thread.currentThread().interrupt();
            throw exception;
        }
    }

    private static void invokeStart(IntextorModule module) throws Exception {
        if (module instanceof LifecycleAwareModule) {
            ((LifecycleAwareModule) module).onStart();
        }
    }

    private void rejectSelfWait(String moduleName, String action) {
        if (executingModules.get().contains(moduleName)) {
            throw new IllegalStateException("A module cannot " + action + " itself while it is executing: " + moduleName);
        }
    }

    private static boolean isTransitioning(State state) {
        return state == State.STARTING || state == State.PAUSING
                || state == State.RESUMING || state == State.STOPPING;
    }

    private static final class Entry {
        private final IntextorModule module;
        private State state = State.NEW;
        private int activeExecutions;
        private Thread transitionOwner;

        private Entry(IntextorModule module) {
            this.module = module;
        }
    }
}
