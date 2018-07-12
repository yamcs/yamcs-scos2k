package org.yamcs.scos2k;

/**
 * Implements logarithmic calibration as specified in
 *
 * EGOS-MCS-S2K-ICD-0001 v6.9 2010-07-06
 *
 * Y = 1 / [A0 + A1*ln(X) + A2*ln2(X) + A3*ln3(X) + A4 *ln4(X)]
 *
 * (ln is natural logairthm)
 * 
 * @author nm
 *
 */
public class LogCalibration {
  public static double calibrate(double v, double... a) {
        double u = Math.log(v);
        double r = 0;
        for (int i = a.length - 1; i >= 0; i--) {
            r = u * r + a[i];
        }
        return 1/r;
    }
}
