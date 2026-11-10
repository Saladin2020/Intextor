# Intextor

Intextor is a small Java 8 execution kernel for registering and running independent modules. The original `CallClass` and `CI_Run` classes remain available for compatibility with the first prototype; new code should use `IntextorModule`, `ModuleRegistry`, and `ExecutionEngine`.

## Current capabilities

- Register and look up named modules through `ModuleRegistry`.
- Run a module synchronously, asynchronously, in parallel with other modules, or after a delay.
- Pass input and output values through a thread-safe `ExecutionContext`.
- Observe execution start, success, and failure with `ModuleEventBus`.
- Load plugin-provided modules with Java's `ServiceLoader` through `ModuleProvider`.
- Start, pause, resume, stop, and restart modules through the engine lifecycle controls.
- Let running modules invoke other modules and merge their outputs into the caller's context.
- Define dependency-ordered workflows and inspect each step's status, timing, and output.

## Example

```java
ModuleRegistry registry = new ModuleRegistry();
registry.register(new primeNumber());

try (ExecutionEngine engine = new ExecutionEngine(registry)) {
    ExecutionContext context = new ExecutionContext(
            Collections.<String, Object>singletonMap("limit", 100));
    engine.execute("primeNumber", context);
    List<Integer> primes = context.get("primes", List.class);
}
```

For asynchronous execution, use `engine.submit(name, context)` and retain the returned `Future` to wait for or cancel the work. `executeParallel` submits each named module to the worker pool. `schedule` runs a module once after a delay.

## Cooperative modules and workflows

While a module is executing, it can call another module through its context:

```java
public void execute(ExecutionContext context) throws Exception {
    ExecutionResult result = context.call("normalizeInput");
    context.put("summary", result.getOutput().get("normalized"));
}
```

Each nested call gets a child execution ID linked to its parent. Successful child context values merge back into the caller. `context.invoke(name)` returns a result that may represent failure; `context.call(name)` throws when the child fails.

Use `WorkflowPlan` when the call order should be declared outside individual modules. A step can depend on earlier step IDs, receive workflow inputs and dependency outputs, and exposes an `ExecutionResult` in `WorkflowResult`:

```java
WorkflowPlan plan = new WorkflowPlan("prepare-report")
        .addStep("load", "loadData")
        .addStep("summarize", "summarizeData",
                Collections.singletonList("load"), Collections.<String, Object>emptyMap());
WorkflowResult result = engine.executeWorkflow(plan, new ExecutionContext());
```

Workflow steps execute in dependency order and stop at the first failed step. Independent work can also be launched explicitly with `executeParallel`.

The current direction is Intextor 2.0, a cooperative modular runtime. Autonomous goal selection and AI planning remain a later design phase; they are not needed for module coordination to work.

Modules start automatically on their first execution. Call `startModule`, `pauseModule`, `resumeModule`, `stopModule`, or `restartModule` for explicit lifecycle control. Pause blocks new executions and waits for current executions to finish before calling `onPause`; it does not suspend a running Java thread. Stop uses the same drain behavior before calling `onStop`. Modules that need resource hooks can implement `LifecycleAwareModule` and override `onStart`, `onPause`, `onResume`, and `onStop`.

## Plugin registration

Implement `ModuleProvider` in a plugin JAR and add a service descriptor at `META-INF/services/system.core.ModuleProvider` containing the provider's fully qualified class name. `engine.loadPlugins(pluginId, classLoader)` registers modules from a caller-owned class loader; `engine.loadPluginJar(pluginId, jarUrl)` creates an isolated loader for one JAR. A plugin can depend on already-loaded plugins by passing their IDs to `loadPluginJar(pluginId, jarUrl, dependencies)`; dependency classes are searched before the base application class path, while Java and Intextor API classes remain shared and each plugin's own implementation classes remain private to its loader. Module names must be unique across the engine.

Use `engine.unloadPlugin(pluginId)` to stop its modules, cancel their pending schedules, remove their lifecycle state, and close an engine-owned JAR loader. A plugin with dependents cannot be unloaded until those dependents are unloaded. Caller-owned class loaders remain the caller's responsibility.

For recurring work, use `scheduleAtFixedRate` for a fixed cadence or `scheduleWithFixedDelay` to wait between completed runs. Keep the returned `ScheduledFuture` to cancel the schedule; pause, stop, restart, plugin unload, and engine close also cancel schedules for the affected modules. An uncaught module error ends that recurring schedule, following `ScheduledExecutorService` behavior.
