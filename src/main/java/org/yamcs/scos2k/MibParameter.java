package org.yamcs.scos2k;

import org.yamcs.scos2k.MonitoringData.PcfRecord;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.ParameterType;

public class MibParameter {
    final PcfRecord pcf;

    MibParameter(PcfRecord pcf) {
        this.pcf = pcf;
    }

    public ParameterType ptype;

    boolean hasCalibrator;

    public DataEncoding getEncoding() {
        return ((BaseDataType) ptype).getEncoding();
    }

    public boolean isSynthetic() {
        return pcf.isSynthetic();
    }

    public String name() {
        return pcf.name;
    }

    public int ptc() {
        return pcf.ptc;
    }

    public int pfc() {
        return pcf.pfc;
    }

    public String getTypeName() {
        StringBuilder sb = new StringBuilder();
        sb.append("ptcpfc_").append(pcf.ptc).append("_").append(pcf.pfc)
                .append("_").append(pcf.categ)
                .append("_").append(pcf.natur);

        sb.append("_");
        if (pcf.curtx != null) {
            sb.append(pcf.curtx);
        }

        sb.append("_");
        if (pcf.inter != null) {
            sb.append(pcf.inter);
        }

        sb.append("_").append(pcf.vplb);
        if (pcf.unit != null) {
            sb.append("_").append(pcf.unit.replace("/", "").replace("%", ""));
        }

        return sb.toString();
    }


}
