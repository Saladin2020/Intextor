/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package intextor.views;

import intextor.controllers.primeNumber;
import intextor.controllers.primeReport;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import system.core.AutonomousRuntime;
import system.core.CapabilityDecisionEngine;
import system.core.ExecutionEngine;
import system.core.Goal;
import system.core.GoalResult;
import system.core.ModuleRegistry;

/**
 *
 * @author Saladin2020
 */
public class display {
    public display() throws Exception {
        ModuleRegistry registry = new ModuleRegistry();
        registry.register(new primeNumber());
        registry.register(new primeReport());
        try (ExecutionEngine engine = new ExecutionEngine(registry)) {
            AutonomousRuntime runtime = new AutonomousRuntime(engine, new CapabilityDecisionEngine());
            Goal goal = new Goal("Summarize prime numbers through 100",
                    Arrays.asList("math.primes.summarize"),
                    Collections.<String, Object>singletonMap("limit", 100));
            GoalResult result = runtime.execute(goal);
            if (!result.isSuccessful()) {
                throw new IllegalStateException("Goal execution failed");
            }
            List<?> primes = (List<?>) result.getWorkflowResult().getOutput().get("primes");
            Integer primeCount = (Integer) result.getWorkflowResult().getOutput().get("primeCount");
            System.out.println("Found " + primeCount + " prime numbers up to 100: " + primes);
        }
    }
}
