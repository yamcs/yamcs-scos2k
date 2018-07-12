package org.yamcs.scos2k.ol;

import java.util.Arrays;
import java.util.List;

import org.yamcs.algorithms.AlgorithmException;
import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.algorithms.AlgorithmExecutor;
import org.yamcs.algorithms.AlgorithmExecutorFactory;
import org.yamcs.xtce.CustomAlgorithm;

public class OLExecutorFactory implements AlgorithmExecutorFactory {
    
    @Override
    public AlgorithmExecutor makeExecutor(CustomAlgorithm alg, AlgorithmExecutionContext execCtx) throws AlgorithmException {
        return new OLExecutor(alg, execCtx);
    }

    @Override
    public List<String> getLanguages() {
        return Arrays.asList(OLAlgorithmEngine.LANGUAGE_NAME);
    }

}
