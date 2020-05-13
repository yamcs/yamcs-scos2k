package org.yamcs.scos2k;
import org.yamcs.algorithms.AlgorithmManager;
import org.yamcs.scos2k.ol.OLAlgorithmEngine;
import org.yamcs.Plugin;

public class Scos2kPlugin implements Plugin {

    public Scos2kPlugin() {
        AlgorithmManager.registerAlgorithmEngine(OLAlgorithmEngine.LANGUAGE_NAME, new OLAlgorithmEngine());
    }
}
