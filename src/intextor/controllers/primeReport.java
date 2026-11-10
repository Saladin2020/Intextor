package intextor.controllers;

import java.util.List;
import system.core.ExecutionContext;
import system.core.IntextorModule;

/** Example coordinator module that calls primeNumber and summarizes its output. */
public final class primeReport implements IntextorModule {
    @Override
    public String getName() {
        return "primeReport";
    }

    @Override
    public void execute(ExecutionContext context) throws Exception {
        context.call("primeNumber");
        List<?> primes = context.get("primes", List.class);
        context.put("primeCount", Integer.valueOf(primes == null ? 0 : primes.size()));
    }
}
