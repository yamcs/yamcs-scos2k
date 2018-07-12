package org.yamcs.scos2k;

import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.ParameterType;

/**
 * 
 * Stores temporarily the values from PCF table here until creating the XTCE parameters
 * 
 */
public class MibParameter {
    final String name;
    final String descr;
    final int ptc;
    final int pfc;
    String pcfCateg;
    String pcfCurtx;
    String pcfNatur;
    String pcfInter;
    int vplb;
    int width;
    public int pid;
    public ParameterType ptype;
    public String unit;
    boolean hasCalibrator;
    boolean uscon;
    String parval;
    
    public MibParameter(String pname, String descr, int ptc, int pfc) {
        this.name = pname;
        this.descr = descr;
        this.ptc = ptc;
        this.pfc = pfc;
    }

    public boolean isSynthetic() {
        return "D".equals(pcfNatur);
    }

    public DataEncoding getEncoding() {
        return ((BaseDataType)ptype).getEncoding();
    }
}
