package system.core;

import java.util.Date;

/** Immutable notification emitted around a module execution. */
public final class ModuleEvent {
    public enum Type { STARTED, SUCCEEDED, FAILED }

    private final Type type;
    private final String moduleName;
    private final String executionId;
    private final Date timestamp;
    private final Throwable error;

    public ModuleEvent(Type type, String moduleName, String executionId, Throwable error) {
        this.type = type;
        this.moduleName = moduleName;
        this.executionId = executionId;
        this.timestamp = new Date();
        this.error = error;
    }

    public Type getType() { return type; }
    public String getModuleName() { return moduleName; }
    public String getExecutionId() { return executionId; }
    public Date getTimestamp() { return new Date(timestamp.getTime()); }
    public Throwable getError() { return error; }
}
