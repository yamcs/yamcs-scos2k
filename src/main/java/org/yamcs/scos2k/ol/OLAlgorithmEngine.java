package org.yamcs.scos2k.ol;

import org.yamcs.YConfiguration;
import org.yamcs.algorithms.AlgorithmEngine;
import org.yamcs.algorithms.AlgorithmExecutorFactory;
import org.yamcs.algorithms.AlgorithmManager;

public class OLAlgorithmEngine implements AlgorithmEngine {
    public final static String LANGUAGE_NAME = "SCOS2K-OL";
    
    @Override
    public AlgorithmExecutorFactory makeExecutorFactory(AlgorithmManager algorithmManager, 
            String language,  YConfiguration config) {
        return new OLExecutorFactory();
    }
    

}
