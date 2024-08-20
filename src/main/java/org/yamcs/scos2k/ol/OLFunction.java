package org.yamcs.scos2k.ol;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.parameter.RawEngValue;
import org.yamcs.utils.TimeEncoding;

public class OLFunction {
    static Map<String, String> javaFunction = new HashMap<>();
    static {
        javaFunction.put("sin", "Math.sin");
        javaFunction.put("arcsin", "Math.asin");
        javaFunction.put("cos", "Math.cos");
        javaFunction.put("arccos", "Math.acos");
        javaFunction.put("tan", "Math.tan");
        javaFunction.put("arctan", "Math.atan");
        javaFunction.put("cotan", "org.yamcs.scos2k.ol.cotan");
        javaFunction.put("arccotan", "org.yamcs.scos2k.ol.cotan");
        javaFunction.put("abs", "Math.abs");
    }
    static String getJavaFunctionName(String olName, int argCount) {
        if(argCount==2 && "arctan".equals(olName)) {
            return "Math.atan2";
        }
        return javaFunction.get(olName);
    }
    
    public static double cotan(double x) {
        return 1/Math.tan(x);
    }
    
    public static double arccotan(double x) {
        return Math.atan(1 / x);
    }
    
    public static boolean synth(RawEngValue... pv) {
        return false;
    }
    
    public static double getObTime(RawEngValue pv) {
        return TimeEncoding.toUnixMillisec(pv.getGenerationTime())/1000.0;
    }
    
    public static int bool2int(boolean v) {
        return v?1:0;
    }
    
}
