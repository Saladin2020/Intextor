package system.core;

import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Shared input/output values and identity for one module execution. */
public final class ExecutionContext {
    private final String executionId;
    private final ConcurrentHashMap<String, Object> values;

    public ExecutionContext() {
        this(UUID.randomUUID().toString(), Collections.<String, Object>emptyMap());
    }

    public ExecutionContext(Map<String, ?> initialValues) {
        this(UUID.randomUUID().toString(), initialValues);
    }

    private ExecutionContext(String executionId, Map<String, ?> initialValues) {
        if (initialValues == null) {
            throw new IllegalArgumentException("initialValues must not be null");
        }
        this.executionId = executionId;
        this.values = new ConcurrentHashMap<String, Object>();
        for (Map.Entry<String, ?> entry : initialValues.entrySet()) {
            put(entry.getKey(), entry.getValue());
        }
    }

    public String getExecutionId() {
        return executionId;
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
}
