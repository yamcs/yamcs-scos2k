package org.yamcs.scos2k;

import java.util.ArrayList;
import java.util.List;

import org.yamcs.xtce.SplinePoint;

public class MonitoringData {

    /**
     * Stores the values from PCF table here until creating the XTCE parameters
     */
    static public class PcfRecord {
        /**
         * Name of the parameter.
         * <p>
         * An alphanumeric string uniquely identifying the monitoring parameter.
         * </p>
         * <p>
         * Note that the OL syntax uses the starting character(s) of OL components to identify their nature.
         * Consequently, no monitoring parameter name is allowed to start with the strings "VAR", "GVAR", or "$".
         * </p>
         * <p>
         * Additionally, the following parameter names are reserved for groundstation telemetry and should not be used
         * for spacecraft parameter names: {@code NNOiiii}, {@code CEBiiii}, {@code KRUiiii}, {@code MSPiiii},
         * {@code VL2iiii}, {@code PERiiii}, {@code REDiiii}, {@code MLGiiii}. Here, <i>iiii</i> is a 4-digit number
         * ranging from 0000 to 9999 (e.g., {@code NNO0000}, {@code NNO0001}, {@code NNO9999}).
         * </p>
         */
        final String name;

        /**
         * Parameter Description. Free textual description of the parameter.
         */
        final String descr;

        final int ptc;
        final int pfc;

        /**
         * 'Padded' width of this parameter, expressed in number of bits. This field is only used when extracting
         * parameter samples using the VPD definition (see Section 3.3.2.5.2 below). If specified, it is used by the
         * SCOS-2000 VPD application to identify the bit position where the next telemetry parameter starts. This can be
         * useful when the slot allocated to a parameter does not correspond to the parameter length implied by the
         * parameter {@code PTC}/{@code PFC} pair, such as in cases where padding bits are introduced to byte-align the
         * next parameter value.
         * <p>
         * In particular, this field can be used for parameters of type deduced ({@code PCF_PTC = 11}) in the following
         * two ways:
         * </p>
         * <ul>
         * <li><b>a)</b> If the parameter of type deduced is associated with a {@code PCF_WIDTH}, it means that the TM
         * values shall be extracted from a fixed-width field (as opposed to a variable width depending on the type of
         * the parameter value being downlinked).</li>
         * <li><b>b)</b> If the parameter of type deduced has no value associated with it in {@code PCF_WIDTH}, then the
         * width of the value to be extracted is determined based on the type of the parameter being downlinked, padded
         * as specified in {@code PCD_WIDTH} of that parameter, if not null.</li>
         * </ul>
         * <p>
         * Note that this field is not used to determine the length of parameters extracted from fixed TM packets using
         * the PLF definition; this is implicit in the definition of the parameters {@code PTC} and {@code PFC} (see
         * Appendix A).
         * </p>
         */
        int width;

        /**
         * Name of a monitoring parameter to be used for one of the following purposes:
         * <ul>
         * <li>For Saved Synthetic Parameters ({@code PCF_NATUR = 'S'}), this field is used to identify the name of the
         * synthetic parameter, whose computed value is used for archival within synthetic parameter packets. Each Saved
         * Synthetic Parameter is not directly associated with an expression but rather with another synthetic parameter
         * (see Section 3.3.2.1.2 below for further clarification).</li>
         * <li>For telemetry parameters of type 'deduced' ({@code PCF_PTC = 11}), this field is used to identify the
         * name of the telemetry parameter (referred to as Parameter# in [RD-6]) whose value provides the on-board
         * parameter ID. The on-board parameter ID is used to interpret parameters of type 'deduced' in variable TM
         * packets (see Section 3.3.2.5.2 below).</li>
         * </ul>
         */

        String related;

        /**
         * Calibration category of the parameter.
         * <p>
         * <b>‘N’</b> - This option is used for:
         * <ul>
         * <li>Numerical parameters associated with one or more numerical, polynomial, or logarithmic calibration
         * definitions</li>
         * <li>Numerical parameters not associated with any calibration definition</li>
         * <li>Parameters with PCF_PTC = 6, 7, 9, or 10 (i.e., all non-numerical parameters except Character String
         * parameters, for which option ‘T’ shall be used)</li>
         * </ul>
         * <p>
         * <b>‘S’</b> - for Status. This option is used for parameters associated with a textual calibration definition.
         * <p>
         * <b>‘T’</b> - for Text. This option is used for parameters of type Character String (PCF_PTC = 8).
         */
        String categ;
        /**
         * Nature of the parameter.
         * <p>
         * <b>‘R’</b> – Raw telemetry parameter (i.e., a monitoring parameter whose value is extracted from TM packets).
         * <br>
         * <b>‘D’</b> – Dynamic OL parameter (i.e., a synthetic parameter specified in Operations Language and not
         * compiled. One ASCII file containing the OL expression associated with this parameter must exist). <br>
         * <b>‘P’</b> – Synthetic Parameters Expression Language (SPEL) based dynamic parameter (i.e., a synthetic
         * parameter specified using the SPEL language and not compiled. One ASCII file containing the SPEL expression
         * associated with this parameter must exist). <br>
         * <b>‘H’</b> – Hard Coded parameter (i.e., a synthetic parameter which has been directly specified in C++ or a
         * synthetic parameter which was initially defined in OL and eventually compiled). <br>
         * <b>‘S’</b> – Saved Synthetic parameter (i.e., a synthetic parameter whose value is calculated based on the
         * expression associated with the parameter specified in PCF_RELATED. The calculated value is then saved in
         * synthetic packets). <br>
         * <b>‘C’</b> – Constant parameter (also referred to as ‘static’ User Defined Constant, i.e., a user-defined
         * parameter for which a static value is specified in the field PCF_PARVAL). It is not allowed to associate this
         * parameter also to a PLF entry describing its position in the ‘constants TM packet’.
         */
        String natur;

        /**
         * Parameter calibration identification name. Monitoring parameters can be associated with zero, one, or
         * multiple calibration definitions. This field can be used to specify the name of the calibration to be used
         * for parameters associated with a single calibration definition. The CUR table shall be used to associate a
         * parameter with multiple calibration definitions.
         * <p>
         * Depending on the parameter category, this field stores the numerical calibration or the textual calibration
         * identification name.
         * <ul>
         * <li>If not null, the value specified in this field shall match TXF_NUMBR (if PCF_CATEG = ‘S’) or
         * CAF_NUMBR/MCF_IDENT/LGF_IDENT (if PCF_CATEG = ‘N’) of the corresponding calibration definition (textual,
         * numerical, polynomial, or logarithmic, respectively).</li>
         * <li>This field cannot be null for status parameters (PCF_CATEG = ‘S’).</li>
         * <li>It must be left null for textual parameters (PCF_CATEG = ‘T’), for string parameters (PCF_PTC = 7 or 8),
         * for time parameters (PCF_PTC = 9 or 10), as well as for all parameters associated with calibration
         * definition(s) in the CUR table (field CUR_PNAME).</li>
         * </ul>
         */
        String curtx;

        /**
         * Flag controlling the extrapolation behavior for parameters calibrated using a numerical calibration curve.
         * <ul>
         * <li><b>'P'</b> - If a raw value outside the calibration curve is received, a valid engineering value is
         * calculated by extrapolating the first two calibration points (if the raw value is outside the calibration
         * range on the lower side) or the last two calibration points (if the raw value is outside the calibration
         * range on the upper side).</li>
         * <li><b>'F'</b> - If a raw value outside the calibration curve is received, an invalid engineering value is
         * returned.</li>
         * </ul>
         * This field is only relevant if the parameter is associated with a numerical calibration (see description of
         * field {@code PCF_CURTX}). In the case of textual calibration (i.e., {@code PCF_CATEG = 'S'}), if a raw value
         * not associated with any text string is received, an invalid engineering value set to all stars ('****') is
         * returned.
         * <p>
         * SCOS-2000 does not use the value of {@code PCF_INTER}. Instead, the extrapolation behavior is determined by
         * the associated numerical calibration curve (which uses the {@code CAF_INTER} flag). This implies that all
         * parameters sharing a calibration use the same extrapolation behavior. On the other hand, different
         * calibrations associated with the same parameter may use different extrapolation behaviors.
         * </p>
         */
        String inter;

        /**
         * Flag controlling the execution of status consistency checks for this parameter.
         * <ul>
         * <li><b>'Y'</b> - The parameter must undergo status consistency checks.</li>
         * <li><b>'N'</b> - The parameter does not require status consistency checks.</li>
         * </ul>
         * This field must be set to <b>'Y'</b> if the OCP table (see Section 3.3.2.3.2 below) contains a status
         * consistency entry for this parameter.
         * <p>
         * Note that there is a hard limit on the maximum number of monitoring parameters that can be associated with a
         * Status Consistency check. This limit is dictated by the maximum allowed size of the SCOS-2000 packet
         * containing the snapshot state of all Status Consistency checks.
         * </p>
         * <p>
         * Starting from release 4.1, this limit can be modified by setting the {@code MAX_SCC_MESSAGE_LENGTH}
         * MISCconfig variable (4-byte integer).
         * </p>
         */
        boolean uscon;

        int vplb;

        /**
         * On-board ID of the telemetry parameter. This field allows the establishment of a one-to-one correspondence
         * between on-ground parameter ID and on-board "Parameter#" identifier (see [RD-6]).
         * <p>
         * It is used to identify the parameter for which values are being delivered in a PUS-compatible variable
         * packet. The on-board parameter ID shall be unique, i.e., it is not allowed to associate several telemetry
         * parameters to the same on-board ID. In case two parameters are associated with the same PCF_PID, the last
         * imported one will be used by SCOS-2000.
         * </p>
         * <p>
         * Value is to be left null when there is no corresponding on-board parameter identifier, e.g., ‘ground only’
         * parameters defined by the user.
         * </p>
         * <p>
         * Unsigned integer number in the range (0 ... 2^32 - 1).
         * </p>
         */
        public int pid;

        public String unit;

        /**
         * Default raw value of the constant parameter.
         * <p>
         * Only applicable if the parameter is a ‘static’ UDC (PCF_NATUR = ‘C’), in which case it is a mandatory field.
         * <p>
         * The value must be expressed in a format compatible with the parameter type (as determined by the PCF_PTC and
         * PCF_PFC).
         * 
         */
        String parval;

        public PcfRecord(String pname, String descr, int ptc, int pfc) {
            this.name = pname;
            this.descr = descr;
            this.ptc = ptc;
            this.pfc = pfc;
        }

        public boolean isSynthetic() {
            return "D".equals(natur);
        }

        @Override
        public String toString() {
            return "PcfRecord [name=" + name + ", descr=" + descr + ", ptc=" + ptc + ", pfc=" + pfc + ", pcfCateg="
                    + categ + ", natur=" + natur + ", curtx=" + curtx + ", inter=" + inter + ", vplb="
                    + vplb + ", width=" + width + ", pid=" + pid + ", unit=" + unit
                    + ", uscon=" + uscon + ", related="
                    + related + "]";
        }

    }

    /**
     * PID: Packets identification
     */
    static class PidRecord {
        /**
         * Type of the TM source packet
         * <p>
         * Integer number in the range (0....255).
         * <p>
         * If set to 0, it means that this packet is not associated to any specific type (e.g. it does not contain a PUS
         * Data Field Header).
         * 
         */
        int type;
        /**
         * Subtype of the TM source packet
         * <p>
         * Integer number in the range (0....255).
         * <p>
         * If set to 0, it means that this packet is not associated to any specific subtype (e.g. it does not contain a
         * PUS Data Field Header).
         * 
         */
        int stype;
        /**
         * APID of the TM source packet.
         * <p>
         * Integer number in the range (0....2047) for TM source packets generated by the spacecraft (see [RD-6]) and in
         * the range (2048....65535) for any other SCOS-2000 TM packet (e.g. TM packets generated by SCOS-2000
         * applications or by other control domains such as SCOE equipments).
         */
        int apid;
        /**
         * Value of the first packet additional identification field (e.g. value ofStructure ID, Task ID, etc.). It is
         * used, together with the packet APID, Type and Sub-type, to identify the Telemetry Packet structure and to
         * interpret its content (if relevant).
         * <p>
         * Unsigned integer number in the range (0....2^31-1). In case the PIC table is not used, 0 value should be
         * entered as field value here.
         * <p>
         * This is because TYPE/STYPE/APID and PI1_VAL(=0) and PI2_VAL(=0) will be used for searching in the PID table
         * and identify the applicable SPID/TPSD.
         * 
         */
        long pi1;
        /**
         * Value of the second packet additional identification field (e.g. value of Function ID, etc.). It is used,
         * together with the packet APID, Type, Sub-type and first additional field, to identify the Telemetry Packet
         * structure and to interpret its content (if relevant).
         * <p>
         * Unsigned integer number in the range (0....2^31-1). In case the PIC table is not used, 0 value should be
         * entered as field value here .
         * <p>
         * This is because TYPE/STYPE/APID and PI1_VAL(=0) and PI2_VAL(=0) will be used for searching in the PID table
         * and identify the applicable SPID/TPSD.
         * 
         */
        long pi2;
        /**
         * The SCOS-2000 Telemetry Packet Number (also referred to as the M SCOS-2000 Packet Id). This field uniquely
         * identifies the structure of TM packets defined in the PLF.
         * <p>
         * In all SCOS-2000 releases up to S2K-R5 it is also used to determine the history file in which this packet is
         * archived. Unsigned integer number in the range (1....2^32-1) (note that zero is not allowed).
         * 
         */
        long spid;
        /**
         * Textual description of Telemetry Packet.
         * 
         * <p>
         * Where applicable (see field PID_EVENT below), it is used by the telemetry packetiser in order to associate an
         * appropriate message text to the SCOS-2000 Event Packets generated on reception of this telemetry source
         * packet.
         * 
         */
        String descr;
        /**
         * On-board subsystem. This field could be used to group packets in a meaningful manner for filtering purposes
         * (future extension).
         * 
         */
        int dfhsize;

        public int getApidTypeSubtype() {
            return (apid << 16) | (type << 8) | stype;
        }

        @Override
        public String toString() {
            return "PidRecord [type=" + type + ", stype=" + stype + ", apid=" + apid + ", pi1=" + pi1 + ", pi2=" + pi2
                    + ", spid=" + spid + ", descr=" + descr + ", dfhsize=" + dfhsize + "]";
        }

    }

    /**
     * PIC: Packets Identification Criteria
     */
    static class PicRecord {
        /**
         * Type of the TM source packet. Integer number in the range (0....255).
         * 
         */
        int type;

        /**
         * Sub-type of the TM source packet (see [RD-6]) Integer number in the range (0....255).
         */
        int stype;
        /**
         */
        int apid;

        /**
         * This field identifies the offset of the first identification field M starting from the beginning of the TM
         * source packet (i.e. the beginning of the SCOS-2000 packet body).
         * <p>
         * Integer number of bytes (value to be set to -1 if no additional identification field is necessary for this
         * packet type/subtype combination).
         * 
         */
        int pi1Offset;
        /**
         * Width of the first additional packet identification field expressed in number of bits.
         * 
         */
        int pi1Width;
        /**
         * This field identifies the offset of the second identification field (ifany) starting from the beginning of
         * the TM source packet (i.e. the beginning of the SCOS-2000 packet body).
         * <p>
         * Integer number of bytes (value to be set to -1 if the second additional identification field is not relevant
         * to this packet type/subtype combination).
         * 
         */
        int pi2Offset;
        /**
         * Width of the second additional packet identification field expressed M in number of bits.
         * 
         */
        int pi2Width;

        public int getTypeSubtype() {
            return (type << 8) | stype;
        }

        @Override
        public String toString() {
            return "PicRecord [type=" + type + ", stype=" + stype + ", apid=" + apid + ", pi1Offset=" + pi1Offset
                    + ", pi1Width=" + pi1Width + ", pi2Offset=" + pi2Offset + ", pi2Width=" + pi2Width + "]";
        }

    }

    /**
     * TPCF: Telemetry packets characteristics
     */
    static class TpcfRecord {
        String name;
        int size;

        @Override
        public String toString() {
            return "TpcfRecord [name=" + name + ", size=" + size + "]";
        }

    }

    static class DeducedParameter {
        String pname;
        String refPname;

        public DeducedParameter(String pname, String refPname) {
            this.pname = pname;
            this.refPname = refPname;
        }
    }

    /**
     * PRF: Parameter range sets
     */
    static class PrfRecord {
        List<String> mins = new ArrayList<>();
        List<String> maxs = new ArrayList<>();

        String descr;
        String inter;
        String dspfmt;
        String radix;

        public void addMinMax(String min, String max) {
            mins.add(min);
            maxs.add(max);
        }
    }

    /**
     * OCP: Monitoring checks definition
     */
    static class OcpRecord {

        /**
         * Used to define the order in which the checks are to be applied foeach parameter. The OCP table is expected to
         * be sorted by OCP_NAME and OCP_POS, with the pairs ordered by soft and hard entries.
         * 
         */
        int pos;

        /**
         * Flag identifying the type of monitoring check.
         * <ul>
         * <li><b>'S'</b> - Soft limit check.</li>
         * <li><b>'H'</b> - Hard limit or soft status check.</li>
         * <li><b>'D'</b> - Delta check.</li>
         * <li><b>'C'</b> - Status Consistency (in which case the field {@code PCF_USCON} for the parameter
         * {@code OCP_NAME} must be set to 'Y').</li>
         * <li><b>'E'</b> - Event generation only (no OOL).</li>
         * </ul>
         */
        String type;

        /**
         * Value to be expressed in a format compatible with the {@code OCF_CODIN}. Depending on the check type (field
         * {@code OCP_TYPE}) and the parameter type (field {@code PCF_CATEG}), this field is used in different ways:
         * <ul>
         * <li>As the low limit value (for numerical parameters subject to limit checks, i.e., if
         * {@code PCF_CATEG = 'N'} and {@code OCP_TYPE = 'S'}, 'H' or 'E'). In this case, this field cannot be left
         * null.</li>
         * <li>As the expected status value (for status parameters, i.e., if {@code PCF_CATEG = 'S'} and
         * {@code OCP_TYPE = 'S'} or 'H'). In this case, this field cannot be left null.</li>
         * <li>As the minimum delta limit value (for numerical parameters subject to delta checks, i.e., if
         * {@code PCF_CATEG = 'N'} and {@code OCP_TYPE = 'D'}). In the case of delta checks, this field can be left
         * null, indicating that no minimum delta check shall be applied. Only a positive value or null value is
         * authorized, as it is used to check the delta in both directions (positive and negative deltas).</li>
         * </ul>
         * This field is irrelevant for Status Consistency checks ({@code OCP_TYPE = 'C'}).
         */
        String lvalu;

        /**
         * High limit value to be expressed in a format compatible with {@code OCF_CODIN}. This field is only relevant
         * for limit or delta checks associated with numerical parameters ({@code PCF_CATEG = 'N'}). Depending on the
         * check type ({@code OCP_TYPE}), this field is used in the following ways:
         * <ul>
         * <li>As the high limit value (for numerical parameters subject to limit checks, i.e., if
         * {@code PCF_CATEG = 'N'} and {@code OCP_TYPE = 'S'}, 'H', or 'E'). In this case, this field cannot be left
         * null.</li>
         * <li>As the maximum delta limit value (for numerical parameters subject to delta checks, i.e., if
         * {@code PCF_CATEG = 'N'} and {@code OCP_TYPE = 'D'}). In the case of delta checks, this field can be left
         * null, indicating that no maximum delta check shall be applied. Only a positive value or null value is
         * authorized, as it is used to check the delta in both directions (positive and negative deltas).</li>
         * </ul>
         * This field is irrelevant for checks associated with status parameters ({@code PCF_CATEG = 'S'}) and for
         * Status Consistency checks ({@code OCP_TYPE = 'C'}).
         */
        String hvalu;

        /**
         * Name of the parameter to be used to determine the applicability of this monitoring check. The field
         * {@code OCP_VALPAR} provides the applicability value to be checked against. Note that SCOS-2000 automatically
         * considers the validity of the applicability expression ({@code OCP_RLCHK = OCP_VALPAR}) when evaluating the
         * check applicability.
         * <p>
         * The following restrictions apply to the parameters that can be used for monitoring check applicability:
         * </p>
         * <ul>
         * <li>Only monitoring parameters whose raw representation is of type integer (either signed or unsigned).</li>
         * <li>Only monitoring parameters that are processed by the telemetry model library, i.e., either TM parameters
         * appearing in the PLF definitions or synthetic parameters (of any type).</li>
         * </ul>
         * <p>
         * This field is not used for Status Consistency checks ({@code OCP_TYPE = 'C'}).
         * </p>
         */
        String rlchk;

        /**
         * Raw value of the applicability parameter ({@code OCP_RLCHK}). This value is used to evaluate the check
         * applicability expression ({@code OCP_RLCHK = OCP_VALPAR}).
         * <p>
         * This field is not used for Status Consistency checks ({@code OCP_TYPE = 'C'}).
         * </p>
         */
        long valpar;

        @Override
        public String toString() {
            return "OcpRecord [pos=" + pos + ", type=" + type + ", lvalu=" + lvalu + ", hvalu=" + hvalu + ", rlchk="
                    + rlchk + ", valpar=" + valpar + "]";
        }
    }

    /**
     * CAF: Numerical calibrations
     */
    static class CafRecord {
        @Override
        public String toString() {
            return "CafRecord [engfmt=" + engfmt + ", rawfmt=" + rawfmt + ", radix=" + radix + "]";
        }

        String engfmt;
        String rawfmt;
        String radix;
    }

    /**
     * CCA: Numerical (de-)calibration curves
     */
    static class CcaRecord {
        /**
         * (De-)calibration curve identification name.
         * <p>
         * Alphanumeric string uniquely identifying the commanding numerical (de-) calibration curve definition.
         * 
         */
        String numbr;

        /**
         * Flag identifying the format type for the engineering values specified in the corresponding records of the CCS
         * table.
         * 
         * <p>
         * This format has to be compatible with the engineering format of the parameters associated with this
         * (de-)calibration curve.
         * </p>
         * 
         * <p>
         * Possible values:
         * </p>
         * <ul>
         * <li><b>'I'</b> – signed integer</li>
         * <li><b>'U'</b> – unsigned integer</li>
         * <li><b>'R'</b> – real</li>
         * </ul>
         */
        String engfmt;

        /**
         * Flag identifying the format type for the raw values specified in the corresponding records of the CCS table.
         * 
         * <p>
         * This format must be compatible with the type of the parameters associated with this (de-)calibration curve.
         * </p>
         * 
         * <p>
         * Possible values:
         * </p>
         * <ul>
         * <li><b>'I'</b> – signed integer</li>
         * <li><b>'U'</b> – unsigned integer</li>
         * <li><b>'R'</b> – real</li>
         * </ul>
         */
        String rawfmt;

        /**
         * Flag identifying the radix used for the raw values (CCS_YVALS) specified in the corresponding records of the
         * CCS table. Only applicable for unsigned integer values (i.e., CCA_RAWFMT = 'U').
         * 
         * <p>
         * Note that the radix associated with this calibration curve is allowed to be different from the one
         * (CPC_RADIX/CSP_RADIX) of the command/sequence parameter(s) using it.
         * </p>
         * 
         * <p>
         * Possible values:
         * </p>
         * <ul>
         * <li><b>'D'</b> – Decimal</li>
         * <li><b>'H'</b> – Hexadecimal</li>
         * <li><b>'O'</b> – Octal</li>
         * </ul>
         */
        String radix;
        List<SplinePoint> splines = new ArrayList<>();
    }

    /**
     * CUR: Calibration conditional selection
     */
    static class CurRecord {
        @Override
        public String toString() {
            return "CurRecord [pos=" + pos + ", rlchk=" + rlchk + ", valpar=" + valpar + ", select=" + select + "]";
        }

        int pos;
        String rlchk;
        long valpar;
        String select;
    }

    /**
     * VPD: Variable packet definition
     */
    static class VpdRecord {
        /**
         * Telemetry Packet Structure Definition.
         * <p>
         * Unsigned integer number matching with PID_TPSD of the telemetry packet to be interpreted.
         */
        long tpsd;
        /**
         * Ordinal position of this parameter inside the packet definition. Tobe used by the database editors to deliver
         * the records in the ASCII table in the appropriate order.
         * 
         */
        int pos;
        /**
         * Name of the parameter appearing in the variable telemetry packet. Alphanumeric string matching with PCF_NAME
         * of the monitoring parameter to be processed/displayed.
         * 
         */
        String name;
        /**
         * This value should only be set for parameters which identify arepeat counter (typically referred to as ‘N’ in
         * [RD-6]) or for ‘dummy’ parameters (see field VPD_FIXREP below) which dentify a fixed number of repetitions
         * (see VPD_FIXREP below)It specifies the number of parameters in the group to be repeatedIn other words, the
         * parameters group repeated by this counter is defined by the successive ‘GRPSIZE’ records of the VPD table. It
         * should be noted that the SCOS-2000 VPD application also supports the processing of ‘nested’ structures i.e.
         * it is allowed to nclude a repeat counter parameter in a parameters group to be repeated.
         * 
         */
        int grpSize;
        /**
         * Possible values:
         * <ul>
         * <li><b>0</b> - No repeat.</li>
         * <li><b>Number &gt; 0</b> - This value should only be set for parameters that identify a repeat counter for
         * arrays of fixed size. When set, this value defines the fixed number of repetitions for the group of
         * parameters determined by {@code VPD_GRPSIZE}. Note that parameters for which this value is set must be
         * defined in the PCF, though they are not present in this variable packet. They serve the purpose of
         * identifying the fixed number of repetitions of the array (whose size is identified in
         * {@code VPD_GRPSIZE}).</li>
         * <li><b>-1</b> - (Future extension) The whole packet is used, i.e., continue processing groups of size
         * {@code VPD_GRPSIZE} until the end of the packet is reached. This likely does not allow any further nested
         * groups.</li>
         * </ul>
         */

        int fixRep;

        /**
         * Flag identifying whether this parameter is to be used to determine the structure of the following portion of
         * the packet.
         * <p>
         * <b>‘Y’</b> - If the value obtained from the TM packet is used as a key (TPSD) to interpret the following
         * entries in the packet. <br>
         * <b>‘N’</b> - If this field is not of choice type.
         */
        String choice;
        /**
         * Textual description of the parameter value. It will be used on the displays for the generation of the header
         * line.
         * 
         */
        int offset;

        @Override
        public String toString() {
            return "VpdRecord [pos=" + pos + ", name=" + name + ", grpSize=" + grpSize + ", fixRep=" + fixRep
                    + ", choice=" + choice + ", offset=" + offset + "]";
        }
    }

}
