package system.core;

/** A unit of work that can be registered with the Intextor execution engine. */
public interface IntextorModule {
    String getName();

    void execute(ExecutionContext context) throws Exception;
}
