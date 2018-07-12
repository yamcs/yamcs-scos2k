package org.yamcs.scos2k.ol;

import java.util.HashMap;
import java.util.Map;

/**
 * Used to store OL global variables 
 * Shared between all OL formulas in one processor
 * 
 * @author nm
 *
 */
public class GlobalVariables {
    Map<String, Double> values = new HashMap<>();
    
    public void set(String name, double value) {
        values.put(name, value);
    }
    public double get(String name) {
        Double d = values.get(name);
        if(d == null) {
            return 0;
        } else {
            return d;
        }
    }
}
