package org.yamcs.scos2k;

import org.yamcs.xtce.TimeEpoch;

public class MibConfig {

    // epoch for the time parameters
    TimeEpoch timeEpoch;

    boolean strict = false;
    protected boolean generatePusNamespace;
    String tcoService;
    // number of bytes in the fractional part that are used by the obt handled by the TCO
    // if the parameters or arguments have less than this, they are shifted to the left
    int tcoFineBytes = -1;

    @Override
    public String toString() {
        return "MibConfig [timeEpoch=" + timeEpoch + ", strict=" + strict + ", generatePusNamespace="
                + generatePusNamespace + ", tcoService=" + tcoService + ", tcoFineBytes=" + tcoFineBytes + "]";
    }
}
