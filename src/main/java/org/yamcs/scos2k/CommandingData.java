package org.yamcs.scos2k;

import org.yamcs.xtce.Argument;
import org.yamcs.xtce.MetaCommand;

public class CommandingData {

    /**
     * CPC: Command parameters
     * <p>
     * This table defines the characteristics of command parameters. One record per command parameter.
     */
    static class CpcRecord {
        /**
         * Unique name of the command parameter.
         */
        String pname;

        /**
         * Textual description of the command parameter.
         */
        String descr;
        /**
         * Parameter Type Code
         */
        int ptc;
        /**
         * Parameter Format Code.
         */
        int pfc;

        /**
         * Flag controlling the input and display format of the engineering values for calibrated parameters and for
         * time parameters.
         * <ul>
         * <li>‘A’ – ASCII (if CPC_CATEG=’T’).</li>
         * <li>‘I’ – signed integer (if CPC_CATEG=’C’ and CCA_ENGFMT=‘I’).</li>
         * <li>‘U’ – unsigned integer (if CPC_CATEG=‘C’ and CCA_ENGFMT=‘U’).</li>
         * <li>‘R’ – real (if CPC_CATEG=‘C’ and CCA_ENGFMT=‘R’).</li>
         * <li>‘T’ – absolute time (if CPC_PTC=9).</li>
         * <li>‘D’ – delta/relative time (if CPC_PTC=10).</li>
         * </ul>
         * <p>
         * This field is unused for command parameters of type ‘Command ID’ (CPC_CATEG=’A’), ‘Parameter ID’
         * (CPC_CATEG=’P’) and ‘Deduced’ (CPC_PTC=11).
         */
        String dispfmt;

        /**
         * This is the default radix that the raw representation of the ‘D’ parameter will be displayed in if it is an
         * unsigned integer.
         * <ul>
         * <li>‘D’ – Decimal</li>
         * <li>‘H’ – Hexadecimal</li>
         * <li>‘O’ – Octal</li>
         * </ul>
         * Note, this may be overridden by a mission configurable set of allowed radices.
         */
        String radix;

        /**
         * Engineering unit mnemonic, e.g., ‘VOLT’, to be displayed beside command parameter values.
         */
        String unit;

        /**
         * Flag identifying the type of calibration or special processing associated with the parameter.
         * <ul>
         * <li>‘C’ – numeric calibration, therefore CPC_CCAREF shall not be Null.</li>
         * <li>‘T’ – textual calibration, therefore CPC_PAFREF shall not be Null.</li>
         * <li>‘B’ – both calibrations (not supported yet by SCOS-2000).</li>
         * <li>‘A’ – for parameters of type ‘Command ID’ (in which case CPC_PTC must equal 7 and CPC_PFC must equal
         * 0).</li>
         * <li>‘P’ – for parameters of type ‘Parameter ID’ (in which case CPC_PTC must equal 3).</li>
         * <li>‘N’ – implies no calibration and no special processing.</li>
         * </ul>
         * This field must be left Null or set to ‘N’ for command parameters whose raw representation is not of a
         * numeric nature (i.e., strings, times) and are not of type ‘Command ID’ or ‘Parameter ID’. This field is
         * unused for command parameters of type ‘Deduced’ as the calibration characteristics of these parameters must
         * be derived from the associated TM parameter.
         */
        String categ;

        /**
         * This field contains the name of an existing parameter range set (PRF_NUMBR) to which the parameter will be
         * associated. The ranges defined within the set will determine the possible values for the parameter.
         * <p>
         * The format of the selected range set (PRF_DSPFMT) must be compatible with the type of the command parameter
         * (described by CPC_PTC or CPC_DISPFMT depending on the used representation, i.e., PRF_INTER).
         * <p>
         * This field is unused for command parameters of type ‘Deduced’.
         */
        String prfref;

        /**
         * This field contains the name of an existing numeric calibration curve set (CCA_NUMBR) to which the parameter
         * will be associated. The value formats of the selected curve set (CCA_RAWFMT and CCA_ENGFMT) must be
         * compatible with the type of the command parameter (described by CPC_PTC) and its engineering format
         * (CPC_DISPFMT).
         * <p>
         * It must be Null if CPC_CATEG is Null, ‘A’, ‘P’, ‘N’ or ‘T’. It cannot be Null if CPC_CATEG is ‘C’ or ‘B’.
         * <p>
         * This field is unused for command parameters of type ‘Deduced’ as the calibration characteristics of these
         * parameters must be derived from the associated TM parameter.
         */
        String ccaref;

        /**
         * This field contains the name of an existing textual calibration definition (PAF_NUMBR) to which the parameter
         * will be associated.
         * <p>
         * The aliases contained within the calibration definition may be used as default values for the parameter. The
         * selected alias set (PAF_RAWFMT) must be compatible with the type of the command parameter (described by
         * CPC_PTC).
         * <p>
         * This field must be null if CPC_CATEG is null, ‘A’, ‘P’, ‘N’, or ‘C’. It cannot be null if CPC_CATEG is ‘T’ or
         * ‘B’.
         * <p>
         * This field is unused for command parameters of type ‘Deduced’, as the calibration characteristics of these
         * parameters must be derived from the associated TM parameter.
         */
        String pafref;

        /**
         * Flag identifying the representation used to express the parameter ‘R’ default value.
         * <p>
         * <b>‘R’</b> – If the default value (CPC_DEFVAL) is expressed using a raw representation. <br>
         * <b>‘E’</b> – If the default value (CPC_DEFVAL) is expressed using an engineering representation. This option
         * can only be used for parameters which are associated with a calibration definition (i.e., CPC_CATEG = ‘T’ or
         * ‘C’).
         * <p>
         * This field is unused for command parameters of type ‘Deduced’.
         */
        String inter;

        /**
         * This field contains the default value for this parameter. It is used if specified so in an associated CDF
         * entry (CDF_INTER = 'D’).
         * <p>
         * Associating a default value to a command parameter (as opposed to associating the default value in the
         * command definition) is useful if the same default value needs to be used for all instances of a parameter
         * across all command definitions.
         * <p>
         * The maximum allowed size for this field depends on the maximum allowed size of commands. The parameter
         * default value must be expressed using the appropriate representation as specified in CPC_INTER.
         * <ul>
         * <li>Raw values must be expressed in a format compatible with the type of the parameter (as specified by
         * CPC_PTC and CPC_RADIX for unsigned integers).</li>
         * <li>Engineering values must be expressed in a format compatible with the engineering representation of the
         * parameter (CPC_DISPFMT).</li>
         * </ul>
         * <p>
         * If this command parameter is of type ‘Command ID’ (CPC_CATEG = ‘A’), this field (if not null) shall contain
         * the name of a command (CCF_CNAME) that does not contain any parameter without a default value, i.e., a
         * command that can be fully encoded without user input.
         * <p>
         * If this command parameter is of type ‘Parameter ID’ (CPC_CATEG = ‘P’), this field (if not null) shall contain
         * the name of a monitoring parameter (PCF_NAME) which is associated with an on-board identifier (PCF_PID not
         * null).
         * <p>
         * This field is unused for command parameters of type ‘Deduced’.
         */
        String defval;

        /**
         * Specifies whether absolute time parameters are to be correlated using the applicable time correlation
         * parameters as part of the interpretation of the raw data.
         * <p>
         * <b>‘Y’</b> – Apply time correlation to time parameters. <br>
         * <b>‘N’</b> – Extract time value without applying time correlation.
         * <p>
         * For relative time parameters (PCF_PTC = 10), the value must be set explicitly to ‘N’.
         * <p>
         * It should be noted that the field CPC_OBTID identifies which OBT format and time correlation parameters are
         * to be applied for this parameter. If time correlation is not applied, then knowledge of the OBT time format
         * is required to determine the time reference to which the parameters relate, i.e., the absolute time
         * corresponding to a time of zero. If an OBT ID is not supplied and PCF_CORR = ‘N’, then the time epoch will be
         * defined by the MISCconfig variable TCO_ESA_EPOCH.
         */
        String corr;

        /**
         * Specifies the OBT ID to be used for the extraction of time parameters, either to apply time correlation or to
         * define the time format – specifically the time epoch.
         * <p>
         * The OBT ID is a configuration identifier used by SCOS-2000 to identify each on-board clock supported by the
         * mission. If there are N on-board clocks, the OBT ID will take values from 1 to N.
         * <p>
         * If not specified, the default OBT ID defined for the mission is assumed. The default OBT ID is a control
         * system configuration parameter controlled by the MISCconfig variable TCO_DEFAULT_OBT_ID.
         */
        int obtid;

        /**
         * returns size in bits or -1 if the argument is not fixed size
         */


        @Override
        public String toString() {
            return "CpcRecord [pname=" + pname + ", descr=" + descr + ", ptc=" + ptc + ", pfc=" + pfc + ", dispfmt="
                    + dispfmt + ", radix=" + radix + ", unit=" + unit + ", categ=" + categ + ", prfref=" + prfref
                    + ", ccaref=" + ccaref + ", pafref=" + pafref + ", inter=" + inter + ", defval=" + defval
                    + ", corr=" + corr + ", obtid=" + obtid + "]";
        }
    }

    /**
     * CDF: Commands definition
     * <p>
     * This table defines the structure of the commands in terms of constituent elements. One record per command element
     * (i.e. parameter or fixed area).
     */
    static class CdfRecord {
        /**
         * Type of the command element:
         * <p>
         * <b>‘A’</b> – For a fixed Area. <br>
         * <b>‘F’</b> – For a parameter that has been Fixed and is non-editable. <br>
         * <b>‘E’</b> – For a parameter that is Editable.
         * <p>
         * <b>Note:</b> The parameter cannot be used in the SDF table if CDF_ELTYPE is not set to ‘E’.
         */
        String eltype;

        /**
         * Textual description used for fixed areas only (CDF_ELTYPE = ‘A’).
         */
        String descr;

        /**
         * This field specifies the length of the command element expressed in number of bits.
         * <p>
         * For fixed areas (CDF_ELTYPE = ‘A’), this field is used by SCOS-2000 to determine the element length in order
         * to encode the command. For fixed or editable command parameters, this field is not used by SCOS-2000, as the
         * element length is derived from the PTC/PFC pair of the referenced parameter (CDF_PNAME). However, it is
         * expected that this field is properly populated with the parameter length to ensure consistency in the
         * commands and parameters definition.
         * <p>
         * If the element is a variable-length parameter (e.g., PTC = 7, PFC = 0), this value must be set to zero.
         */
        int ellen;

        /**
         * This field specifies the bit offset of this element relative to the start of the unexpanded command
         * application data field (i.e., the end of the packet header, including the data field header where applicable,
         * as specified in the PCDF table).
         * <p>
         * The bit offset of command elements following a repeated group shall be calculated assuming no repetition
         * (i.e., counters value = 1).
         * <p>
         * This field is only used by the SCOS-2000 command encoding function for sorting the elements constituting a
         * command. It is not used as an absolute reference for locating the command elements within the command. This
         * implies that the command elements associated with a command will be encoded in the order dictated by this
         * field without any ‘gap’ between them.
         * <p>
         * The value must be compatible with preceding command elements with respect to their length and position.
         * <p>
         * Unsigned integer number of bits.
         */
        int position;

        /**
         * If this element is a group repeater parameter, then this field’s value is the number of following command
         * elements belonging to the group to be repeated ‘N’ times at load-time. Otherwise, its value must be left null
         * or set to zero.
         * <p>
         * This field is a table key when CDF_GRPSIZE is non-zero.
         */
        int grpsize;

        CpcRecord cpc;

        /**
         * Flag identifying the representation in which the (fixed or default) ‘R’ value (CDF_VALUE) is expressed for
         * this command element.
         * <p>
         * <b>‘R’</b> - If the value is expressed using a raw representation. <br>
         * <b>‘E’</b> - If the value is expressed using an engineering representation. This option can only be used for
         * parameters associated with a calibration definition (i.e., CPC_CATEG = ‘T’ or ‘C’). <br>
         * <b>‘D’</b> - If the value is to be taken from the corresponding CPC entry (CPC_DEFVAL). <br>
         * <b>‘T’</b> - If the default value is to be taken from the value of the monitoring parameter given by CDF_TMID
         * (dynamic default). This option can only be used for editable parameters (CDF_ELTYPE = ‘E’). Note that a
         * static default value for this parameter must be specified in the CDF_VALUE field using a raw representation.
         * <p>
         * This field is irrelevant for fixed areas (CDF_ELTYPE = ‘A’).
         */
        String inter;

        /**
         * This field specifies the default or fixed value for the given element.
         * <p>
         * If the element type is a fixed area, then this value must be a raw unsigned integer value expressed in
         * hexadecimal. Note that 0 is an authorized value for both fixed areas and group repeaters. The maximum allowed
         * size for this field depends on the maximum allowed size of commands.
         * <p>
         * Note that fixed areas shall not be used for Service 11 command elements that need to be processed for ground
         * modeling (i.e., by the OBQM).
         * <p>
         * If the command element is a (fixed or editable) parameter, this value must be expressed using the appropriate
         * representation, i.e., raw unless option ‘E’ is used in CDF_INTER. Raw values must be expressed in a format
         * compatible with the type of the parameter (as specified by CPC_PTC and CPC_RADIX for unsigned integers).
         * Engineering values must be expressed in a format compatible with the engineering representation of the
         * parameter (CPC_DISPFMT).
         * <p>
         * If this command element is a parameter of type ‘Command ID’ (CPC_CATEG of parameter CDF_PNAME is set to ‘A’),
         * this field (if not null) shall contain the name of a command (CCF_CNAME) that does not contain any parameter
         * without a default value (i.e., a command that can be fully encoded without user input).
         * <p>
         * If this command element is a parameter of type ‘Parameter ID’ (CPC_CATEG of parameter CDF_PNAME is set to
         * ‘P’), this field (if not null) shall contain the name of a monitoring parameter (PCF_NAME) which is
         * associated with an on-board identifier (PCF_PID not null).
         * <p>
         * For fixed areas (CDF_ELTYPE = ‘A’), this field can take a maximum value of 32 bits (FFFF FFFF).
         * <p>
         * This field cannot be left empty (null) if:
         * <ul>
         * <li>It is a fixed area (CDF_ELTYPE = ‘A’),</li>
         * <li>It is a fixed command parameter (CDF_ELTYPE = ‘F’) and its value is taken from this field (CDF_INTER ≠
         * ‘D’),</li>
         * <li>It is an editable command parameter (CDF_ELTYPE = ‘E’) associated with a dynamic default (CDF_INTER =
         * ‘T’).</li>
         * </ul>
         * If the parameter is editable (CDF_ELTYPE = ‘E’) and not associated with a dynamic default (CDF_INTER ≠ ‘T’),
         * then this field may be left null, indicating that the user must enter a value before loading the command.
         * <p>
         * This field is irrelevant if option ‘D’ is used in CDF_INTER.
         */
        String value;

        /**
         * This field contains the name of a monitoring parameter used to extract dynamic default values. This field is
         * only applicable if option ‘T’ is used in CDF_INTER. The PTC/PFC values of the selected monitoring parameter
         * must be the same as the PTC/PFC values of the corresponding command parameter given by CDF_PNAME.
         * <p>
         * Only monitoring parameters that are processed by the telemetry model library, i.e., either TM parameters
         * appearing in the PLF definitions or synthetic parameters (of any type), can be used for this purpose.
         */
        String tmid;

        int vplb = -1;

        @Override
        public String toString() {
            return "CdfRecord [eltype=" + eltype + ", descr=" + descr + ", ellen=" + ellen + ", position=" + position
                    + ", grpsize=" + grpsize + ", cpc=" + cpc + ", inter=" + inter + ", value=" + value + ", tmid="
                    + tmid + ", vplb=" + vplb + "]";
        }
    }

    static class TcHeaderRecord {
        String name;
        MetaCommand mc;
        Argument apid;
        Argument type;
        Argument subType;
        Argument ack;
        int ackLocation;
        int apidLocation;
        int firstArgLocation;
    }

}
