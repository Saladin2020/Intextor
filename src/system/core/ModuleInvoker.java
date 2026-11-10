package system.core;

/** Synchronous module-to-module invocation contract used by ExecutionContext. */
public interface ModuleInvoker {
    ExecutionResult invoke(String moduleName, ExecutionContext context);
}
