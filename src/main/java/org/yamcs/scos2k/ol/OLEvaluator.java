package org.yamcs.scos2k.ol;

import java.util.List;

import org.yamcs.parameter.ParameterValue;

/**
 * Interface implemented by the automatically generated code in {@link BaseOLParser}
 * 
 * @author nm
 *
 */
public interface OLEvaluator {
    /**
     * Evaluates the expression with the given inputs and return the 
     * raw value 
     * 
     * @param inputList
     * @return
     */
    Object evaluate(GlobalVariables globalVariables, List<ParameterValue> inputList);
}
