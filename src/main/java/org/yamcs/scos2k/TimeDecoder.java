package org.yamcs.scos2k;

import java.util.Map;

import org.yamcs.algorithms.AlgorithmExecutionContext;
import org.yamcs.parameter.Value;
import org.yamcs.tctm.ccsds.time.CucTimeDecoder;
import org.yamcs.utils.BitBuffer;
import org.yamcs.utils.ValueUtility;
import org.yamcs.xtce.Algorithm;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtceproc.AbstractDataDecoder;
import org.yamcs.xtceproc.XtceProcessingException;

/**
 * Decodes absolute times in CUC (CCSDS Unsegmented Code)
 *  or CDS (CCSDS Segmented Code) using the (ptc,pfc) as defined in SCOS2k MIB 
 * 
 * @author nm
 *
 */
public class TimeDecoder extends AbstractDataDecoder {
    CucTimeDecoder ctd;
    int ptc;
    int pfc;
    
    public TimeDecoder(Algorithm alg, AlgorithmExecutionContext ctx, Map<String, Integer> arg) {
        if(!arg.containsKey("ptc") || !arg.containsKey("pfc")) {
            throw new XtceProcessingException("ptc or pfc not specified (please use a map {ptc: x, pfc: y}");
        }
        ptc = arg.get("ptc");
        pfc = arg.get("pfc");
        if(ptc!=9) {
            throw new XtceProcessingException("Invalid ptc "+ptc+" specified; should be 9");
        }
        
        if(pfc==0) {
            ctd = new CucTimeDecoder(-1);    
        } else if (pfc<2) {
            throw new XtceProcessingException("pfc "+pfc+" not supported");
        } else if (pfc<30) {
            ctd = new CucTimeDecoder(pfc+29);
        } else if(pfc==30) {
            throw new XtceProcessingException("pfc "+pfc+" not supported");
        } else {
            throw new XtceProcessingException("pfc "+pfc+" not supported");
        }
    }
    /**
     * returns the time as SINT64 representing milliseconds since the epoch
     */
    @Override
    public Value extractRaw(DataEncoding de, BitBuffer buffer) {
        long t;
        try {
            t = ctd.decode(() -> buffer.getByte());
        } catch (Exception e) {
           throw new XtceProcessingException("Could not decode time: "+e.getMessage());
        }
        return ValueUtility.getSint64Value(t);
    }

}
