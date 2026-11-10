package intextor.controllers;

import java.util.List;
import java.util.Collections;
import java.util.Collection;
import system.core.ExecutionContext;
import system.core.CapabilityAwareModule;

/** Example coordinator module that calls primeNumber and summarizes its output. */
public final class primeReport implements CapabilityAwareModule {
    @Override
    public String getName() {
        return "primeReport";
    }

    @Override
    public Collection<String> getCapabilities() {
        return Collections.singletonList("math.primes.summarize");
    }

    @Override
    public void execute(ExecutionContext context) throws Exception {
        context.call("primeNumber");
        List<?> primes = context.get("primes", List.class);
        context.put("primeCount", Integer.valueOf(primes == null ? 0 : primes.size()));
    }
}
