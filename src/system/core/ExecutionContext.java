package system.core;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shared input/output values and identity for one module execution. */
public final class ExecutionContext {
    private final String executionId;
    private final String parentExecutionId;
    private final ConcurrentHashMap<String, Object> values;
    private final ThreadLocal<ModuleInvoker> invoker = new ThreadLocal<ModuleInvoker>();

    public ExecutionContext() {
        this(UUID.randomUUID().toString(), null, Collections.<String, Object>emptyMap());
    }

    public ExecutionContext(Map<String, ?> initialValues) {
        this(UUID.randomUUID().toString(), null, initialValues);
    }

    private ExecutionContext(String executionId, String parentExecutionId, Map<String, ?> initialValues) {
        if (initialValues == null) {
            throw new IllegalArgumentException("initialValues must not be null");
        }
        this.executionId = executionId;
        this.parentExecutionId = parentExecutionId;
        this.values = new ConcurrentHashMap<String, Object>();
        for (Map.Entry<String, ?> entry : initialValues.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    public String getExecutionId() {
        return executionId;
    }

    public String getParentExecutionId() {
        return parentExecutionId;
    }

    /** Creates a child context with a new execution id and a snapshot of these values. */
    public ExecutionContext fork() {
        return new ExecutionContext(UUID.randomUUID().toString(), executionId, snapshot());
    }

    public ExecutionContext fork(Map<String, ?> overrides) {
        if (overrides == null) {
            throw new IllegalArgumentException("overrides must not be null");
        }
        ExecutionContext child = fork();
        for (Map.Entry<String, ?> entry : overrides.entrySet()) {
            child.put(entry.getKey(), entry.getValue());
        }
        return child;
    }

    /** Creates a child context from an explicit value set instead of copying all current values. */
    public ExecutionContext child(Map<String, ?> initialValues) {
        return new ExecutionContext(UUID.randomUUID().toString(), executionId, initialValues);
    }

    /** Calls another module synchronously and merges its values back on success. */
    public ExecutionResult invoke(String moduleName) {
        ModuleInvoker currentInvoker = invoker.get();
        if (currentInvoker == null) {
            throw new IllegalStateException("Module invocation is available only while a module is executing");
        }
        ExecutionContext child = fork();
        ExecutionResult result = currentInvoker.invoke(moduleName, child);
        if (result.isSuccessful()) {
            merge(child.snapshot());
        }
        return result;
    }

    public ExecutionResult call(String moduleName) throws Exception {
        ExecutionResult result = invoke(moduleName);
        if (!result.isSuccessful()) {
            Throwable error = result.getError();
            if (error instanceof Exception) {
                throw (Exception) error;
            }
            if (error instanceof Error) {
                throw (Error) error;
            }
            throw new IllegalStateException("Module call failed: " + moduleName);
        }
        return result;
    }

    public void merge(Map<String, ?> valuesToMerge) {
        if (valuesToMerge == null) {
            throw new IllegalArgumentException("valuesToMerge must not be null");
        }
        for (Map.Entry<String, ?> entry : valuesToMerge.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    public Object get(String key) {
        return values.get(requireKey(key));
    }

    public <T> T get(String key, Class<T> type) {
        if (type == null) {
            throw new IllegalArgumentException("type must not be null");
        }
        Object value = get(key);
        if (value == null) {
            return null;
        }
        if (!type.isInstance(value)) {
            throw new IllegalStateException("Context value '" + key + "' is not a " + type.getName());
        }
        return type.cast(value);
    }

    public void put(String key, Object value) {
        String checkedKey = requireKey(key);
        if (value == null) {
            values.remove(checkedKey);
        } else {
            values.put(checkedKey, value);
        }
    }

    public Map<String, Object> snapshot() {
        return Collections.unmodifiableMap(new ConcurrentHashMap<String, Object>(values));
    }

    private static String requireKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            throw new IllegalArgumentException("Context key must not be blank");
        }
        return key;
    }

    ModuleInvoker bindInvoker(ModuleInvoker newInvoker) {
        ModuleInvoker previous = invoker.get();
        invoker.set(newInvoker);
        return previous;
    }

    void restoreInvoker(ModuleInvoker previous) {
        if (previous == null) {
            invoker.remove();
        } else {
            invoker.set(previous);
        }
    }
}
