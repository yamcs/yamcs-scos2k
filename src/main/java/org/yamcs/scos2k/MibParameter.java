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
    /**
     * Calibration category of the parameter.
     * <p>
     * <b>‘N’</b> - This option is used for:
     * <ul>
     *   <li>Numerical parameters associated with one or more numerical, polynomial, or logarithmic calibration definitions</li>
     *   <li>Numerical parameters not associated with any calibration definition</li>
     *   <li>Parameters with PCF_PTC = 6, 7, 9, or 10 (i.e., all non-numerical parameters except Character String parameters, 
     *   for which option ‘T’ shall be used)</li>
     * </ul>
     * <p>
     * <b>‘S’</b> - for Status. This option is used for parameters associated with a textual calibration definition.
     * <p>
     * <b>‘T’</b> - for Text. This option is used for parameters of type Character String (PCF_PTC = 8).
     */
    String pcfCateg;
    /**
     * Nature of the parameter.
     * <p>
     * <b>‘R’</b> – Raw telemetry parameter (i.e., a monitoring parameter whose value is extracted from TM packets).
     * <br>
     * <b>‘D’</b> – Dynamic OL parameter (i.e., a synthetic parameter specified in Operations Language and not compiled. 
     * One ASCII file containing the OL expression associated with this parameter must exist).
     * <br>
     * <b>‘P’</b> – Synthetic Parameters Expression Language (SPEL) based dynamic parameter (i.e., a synthetic parameter specified 
     * using the SPEL language and not compiled. One ASCII file containing the SPEL expression associated with this parameter must 
     * exist).
     * <br>
     * <b>‘H’</b> – Hard Coded parameter (i.e., a synthetic parameter which has been directly specified in C++ or a synthetic parameter 
     * which was initially defined in OL and eventually compiled).
     * <br>
     * <b>‘S’</b> – Saved Synthetic parameter (i.e., a synthetic parameter whose value is calculated based on the expression 
     * associated with the parameter specified in PCF_RELATED. The calculated value is then saved in synthetic packets).
     * <br>
     * <b>‘C’</b> – Constant parameter (also referred to as ‘static’ User Defined Constant, i.e., a user-defined parameter for which 
     * a static value is specified in the field PCF_PARVAL). It is not allowed to associate this parameter also to a PLF entry 
     * describing its position in the ‘constants TM packet’.
     */
    String pcfNatur;

    /**
     * Parameter calibration identification name. Monitoring parameters can be associated with zero, one, or multiple 
     * calibration definitions. This field can be used to specify the name of the calibration to be used for parameters 
     * associated with a single calibration definition. The CUR table shall be used to associate a parameter with 
     * multiple calibration definitions.
     * <p>
     * Depending on the parameter category, this field stores the numerical calibration or the textual calibration 
     * identification name.
     * <ul>
     *   <li>If not null, the value specified in this field shall match TXF_NUMBR (if PCF_CATEG = ‘S’) or 
     *   CAF_NUMBR/MCF_IDENT/LGF_IDENT (if PCF_CATEG = ‘N’) of the corresponding calibration definition 
     *   (textual, numerical, polynomial, or logarithmic, respectively).</li>
     *   <li>This field cannot be null for status parameters (PCF_CATEG = ‘S’).</li>
     *   <li>It must be left null for textual parameters (PCF_CATEG = ‘T’), for string parameters (PCF_PTC = 7 or 8), 
     *   for time parameters (PCF_PTC = 9 or 10), as well as for all parameters associated with calibration 
     *   definition(s) in the CUR table (field CUR_PNAME).</li>
     * </ul>
     */

    String pcfCurtx;
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
