/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package intextor.views;

import intextor.controllers.primeNumber;
import intextor.controllers.primeReport;
import java.util.Collections;
import java.util.List;
import system.core.ExecutionContext;
import system.core.ExecutionEngine;
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
            ExecutionContext context = new ExecutionContext(Collections.<String, Object>singletonMap("limit", 100));
            engine.execute("primeReport", context);
            List<Integer> primes = context.get("primes", List.class);
            Integer primeCount = context.get("primeCount", Integer.class);
            System.out.println("Found " + primeCount + " prime numbers up to 100: " + primes);
        }
    }
}
