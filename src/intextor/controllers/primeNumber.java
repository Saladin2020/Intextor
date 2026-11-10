/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package intextor.controllers;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import system.core.ExecutionContext;
import system.core.CapabilityAwareModule;

/**
 *
 * @author User
 */
public class primeNumber implements CapabilityAwareModule {

    @Override
    public String getName() {
        return "primeNumber";
    }

    @Override
    public List<String> getCapabilities() {
        return Collections.singletonList("math.primes.generate");
    }

    @Override
    public void execute(ExecutionContext context) {
        Integer limit = context.get("limit", Integer.class);
        if (limit == null) {
            limit = Integer.valueOf(100);
        }
        if (limit.intValue() < 2) {
            throw new IllegalArgumentException("limit must be at least 2");
        }
        List<Integer> primes = new ArrayList<Integer>();
        for (long candidate = 2; candidate <= limit.longValue(); candidate++) {
            boolean prime = true;
            for (long divisor = 2; divisor <= candidate / divisor; divisor++) {
                if (candidate % divisor == 0) {
                    prime = false;
                    break;
                }
            }
            if (prime) {
                primes.add(Integer.valueOf((int) candidate));
            }
        }
        context.put("primes", primes);
    }

}
