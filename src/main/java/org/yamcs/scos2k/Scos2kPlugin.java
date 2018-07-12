package org.yamcs.scos2k;
import org.yamcs.algorithms.AlgorithmManager;
import org.yamcs.scos2k.ol.OLAlgorithmEngine;
import org.yamcs.spi.Plugin;

public class Scos2kPlugin implements Plugin {

    public Scos2kPlugin() {
        AlgorithmManager.registerAlgorithmEngine(OLAlgorithmEngine.LANGUAGE_NAME, new OLAlgorithmEngine());
    }
    
    @Override
    public String getName() {
        return "SCOS2K";
    }

    @Override
    public String getDescription() {
        return "Implements SCOS 2000 MIB loader including executors for OL expressions and decoding of special SCOS 2000 data type";
    }

    @Override
    public String getVersion() {
        return "0.1";
    }

    @Override
    public String getVendor() {
        return "Space Applications Services";
    }
}
