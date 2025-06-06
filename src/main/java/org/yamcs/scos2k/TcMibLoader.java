package org.yamcs.scos2k;

import static org.yamcs.scos2k.MibLoaderBits.getDataEncoding;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.scos2k.MonitoringData.CcaRecord;
import org.yamcs.scos2k.CommandingData.CdfRecord;
import org.yamcs.scos2k.CommandingData.CpcRecord;
import org.yamcs.scos2k.MibLoaderBits.MibLoadException;
import org.yamcs.scos2k.MonitoringData.PrfRecord;
import org.yamcs.tctm.pus.PusCommandPostprocessor;
import org.yamcs.scos2k.CommandingData.TcHeaderRecord;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.AbsoluteTimeArgumentType;
import org.yamcs.xtce.AggregateArgumentType;
import org.yamcs.xtce.AncillaryData;
import org.yamcs.xtce.Algorithm.Scope;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.ArgumentEntry;
import org.yamcs.xtce.ArgumentInstanceRef;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.ArrayArgumentType;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.BinaryArgumentType;
import org.yamcs.xtce.BooleanDataType;
import org.yamcs.xtce.CheckWindow;
import org.yamcs.xtce.CheckWindow.TimeWindowIsRelativeToType;
import org.yamcs.xtce.CommandContainer;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.CommandVerifier.Type;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.mdb.DatabaseLoadException;
import org.yamcs.xtce.EnumeratedArgumentType;
import org.yamcs.xtce.FixedIntegerValue;
import org.yamcs.xtce.FixedValueEntry;
import org.yamcs.xtce.FloatArgumentType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatDataType;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.IntegerArgumentType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.IntegerDataEncoding.Encoding;
import org.yamcs.xtce.IntegerDataType;
import org.yamcs.xtce.IntegerValue;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.NameDescription;
import org.yamcs.xtce.NumericParameterType;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.ReferenceTime;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtce.SequenceEntry.ReferenceLocationType;
import org.yamcs.xtce.Significance;
import org.yamcs.xtce.Significance.Levels;
import org.yamcs.xtce.util.ParameterReference;
import org.yamcs.xtce.SplineCalibrator;
import org.yamcs.xtce.SplinePoint;
import org.yamcs.xtce.TransmissionConstraint;
import org.yamcs.xtce.UnitType;
import org.yamcs.xtce.ValueEnumeration;
import org.yamcs.xtce.ValueEnumerationRange;
import org.yamcs.xtce.XtceDb;

import static org.yamcs.scos2k.MibLoaderBits.*;

public abstract class TcMibLoader extends TmMibLoader {
    Map<String, CpcRecord> cpcRecords = new HashMap<>();
    Significance SIGNIF_CRITICAL = new Significance(Levels.CRITICAL, null);
    Significance SIGNIF_NONE = new Significance(Levels.NONE, null);
    Map<String, List<CdfRecord>> cdfRecords = new HashMap<>();
    Map<String, CcaRecord> ccaRecords = new HashMap<>();
    private Map<String, List<ValueEnumerationRange>> enumerationRanges = new HashMap<>();
    private Map<String, List<ValueEnumeration>> enumerations = new HashMap<>();

    private Map<Integer, EnumeratedArgumentType.Builder> parameterIdArgs = new HashMap<>();
    // default size in bytes of the size tag for variable length strings and bytestrings
    private int vblParamLengthBytes = 1;
    // if true, allow users to change the APID of the commands sent
    private boolean allowApidChange = false;
    // if true, allow users to change the ack flags of the command sent
    private boolean allowAckChange = false;

    private Map<String, TcHeaderRecord> tcHeaderRecords = new HashMap<>();
    Map<String, Argument> headerArgs = new HashMap<>();

    private int uncertaintyPeriod;

    public TcMibLoader(YConfiguration config) throws ConfigurationException {
        super(config);
        YConfiguration tcConf = config.getConfig("TC");
        vblParamLengthBytes = tcConf.getInt("vblParamLengthBytes", 1);
        allowApidChange = tcConf.getBoolean("allowApidChange", false);
        allowAckChange = tcConf.getBoolean("allowAckChange", false);
    }

    protected void loadCommands() {
        loadTcpPcpc();
        loadPcdf();
        loadDecalib();
        loadTxtDecalib();
        loadCpc();
        loadCdf();
        loadCcf();
        loadPtv();
        loadCvp();
    }

    /****** commanding data **********/
    // Packet headers characteristics: tcp
    final static int IDX_TCP_ID = 0;
    final static int IDX_TCP_DESC = 1;

    // Packet headers parameters: pcpc
    final static int IDX_PCPC_NAME = 0;
    final static int IDX_PCPC_DESC = 1;
    final static int IDX_PCPC_CODE = 2;

    // Packet headers definition: pcdf
    final static int IDX_PCDF_TCNAME = 0;
    final static int IDX_PCDF_DESC = 1;
    final static int IDX_PCDF_TYPE = 2;
    final static int IDX_PCDF_LEN = 3;
    final static int IDX_PCDF_BIT = 4;
    final static int IDX_PCDF_PNAME = 5;
    final static int IDX_PCDF_VALUE = 6;
    final static int IDX_PCDF_RADIX = 7;

    private void loadTcpPcpc() throws DatabaseLoadException {
        switchTo("tcp");
        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_TCP_ID);
            String name = line[IDX_TCP_ID];
            if (tcHeaderRecords.containsKey(name)) {
                throw new MibLoadException(ctx, "duplicate definition of TC header '" + name + "'");
            }
            TcHeaderRecord thr = new TcHeaderRecord();
            thr.name = name;
            thr.mc = new MetaCommand(name);
            thr.mc.setAbstract(true);
            thr.mc.setCommandContainer(new CommandContainer(name));
            spaceSystem.addMetaCommand(thr.mc);
            tcHeaderRecords.put(name, thr);
        }
        switchTo("pcpc");
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_PCPC_NAME, IDX_PCPC_DESC);
            String pname = line[IDX_PCPC_NAME];
            if (headerArgs.containsKey(pname)) {
                throw new MibLoadException(ctx, "duplicate definition of TC parameter '" + pname + "'");
            }
            String code = getString(line, IDX_PCPC_CODE, "U");
            Argument arg = new Argument(pname);
            IntegerArgumentType.Builder iat = new IntegerArgumentType.Builder().setName(pname);
            if ("U".equals(code)) {
                iat.setSigned(false);
            } else if ("I".equals(code)) {
                iat.setSigned(true);
            } else {
                throw new MibLoadException(ctx, "invalid  PCPC_CODE=" + code + "");
            }

            arg.setArgumentType(iat.build());
            arg.setShortDescription(line[IDX_PCPC_DESC]);
            headerArgs.put(pname, arg);
        }

    }

    private void loadPcdf() throws DatabaseLoadException {
        switchTo("pcdf");
        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_PCDF_TCNAME, IDX_PCDF_TYPE, IDX_PCDF_LEN, IDX_PCDF_BIT);
            String cname = line[IDX_PCDF_TCNAME];
            TcHeaderRecord thr = tcHeaderRecords.get(cname);
            if (thr == null) {
                throw new MibLoadException(ctx,
                        "Ecountered PCDF record without a corresponding tcp record with TCP_ID '" + cname + "'");
            }
            int location = getInt(line, IDX_PCDF_BIT);
            int sizeInBits = getInt(line, IDX_PCDF_LEN);
            CommandContainer container = thr.mc.getCommandContainer();
            String type = line[IDX_PCDF_TYPE];
            Argument arg = null;

            if (thr.firstArgLocation < location + sizeInBits) {
                thr.firstArgLocation = location + sizeInBits;
            }

            if ("F".equals(type)) {
                checkMandatory(line, IDX_PCDF_VALUE);
                byte[] binary = StringConverter.hexStringToArray(line[IDX_PCDF_VALUE]);
                if (sizeInBits > binary.length * 8) {
                    byte[] tmp = new byte[(sizeInBits >> 3) + 1];
                    System.arraycopy(binary, 0, tmp, tmp.length - binary.length, binary.length);
                    binary = tmp;
                }
                FixedValueEntry fve = new FixedValueEntry(getString(line, IDX_PCDF_DESC, null), binary, sizeInBits);
                fve.setLocation(ReferenceLocationType.CONTAINER_START, location);
                container.addEntry(fve);
            } else if ("ATSK".contains(type)) {
                checkMandatory(line, IDX_PCDF_PNAME);
                arg = headerArgs.get(line[IDX_PCDF_PNAME]);
                if (arg == null) {
                    throw new MibLoadException(ctx,
                            "Ecountered PCDF record without a corresponding pcpc record with PCPC_PNAME '"
                                    + line[IDX_PCDF_PNAME] + "'");
                }
                IntegerArgumentType.Builder iat = (IntegerArgumentType.Builder) arg.getArgumentType().toBuilder();
                IntegerDataEncoding.Builder encoding = (IntegerDataEncoding.Builder) iat.getEncoding();
                if (encoding != null) {
                    if (encoding.getSizeInBits() != sizeInBits) {
                        throw new MibLoadException(ctx,
                                line[IDX_PCDF_PNAME] + ": ecountered PCDF_LEN=" + sizeInBits
                                        + " different than specified before" + encoding.getSizeInBits());
                    }
                } else {
                    encoding = new IntegerDataEncoding.Builder().setSizeInBits(sizeInBits);
                    if (iat.isSigned()) {
                        encoding.setEncoding(Encoding.TWOS_COMPLEMENT);
                    }
                    iat.setEncoding(encoding);
                }
                arg.setArgumentType(iat.build());
                ArgumentEntry ae = new ArgumentEntry(arg);
                ae.setLocation(ReferenceLocationType.CONTAINER_START, location);

                switch (type) {
                case "A":
                    thr.apid = arg;
                    if (allowApidChange) {
                        // the ack argument will be part of the command
                        thr.apidLocation = location;
                    } else {
                        thr.mc.addArgument(arg);
                        container.addEntry(ae);
                    }
                    break;
                case "T":
                    thr.type = arg;
                    thr.mc.addArgument(arg);
                    container.addEntry(ae);
                    break;
                case "S":
                    thr.subType = arg;
                    thr.mc.addArgument(arg);
                    container.addEntry(ae);
                    break;
                case "K":
                    thr.ack = arg;
                    if (allowAckChange) {
                        // the ack argument will be part of the command
                        thr.ackLocation = location;
                    } else {
                        thr.mc.addArgument(arg);
                        container.addEntry(ae);
                    }
                    break;
                }
            } else if ("P".equals(type)) {
                byte[] binary = new byte[(sizeInBits >> 3) + 1];
                FixedValueEntry fve = new FixedValueEntry(getString(line, IDX_PCDF_PNAME, null), binary, sizeInBits);
                fve.setLocation(ReferenceLocationType.CONTAINER_START, getInt(line, IDX_PCDF_BIT));
                container.addEntry(fve);
            } else {
                throw new MibLoadException(ctx, "Invalid PCDF_TYPE=" + type);
            }
        }
    }

    // Command characteristics: ccf
    final static int IDX_CCF_CNAME = 0;
    final static int IDX_CCF_DESCR = 1;
    final static int IDX_CCF_DESCR2 = 2;
    final static int IDX_CCF_CTYPE = 3;
    final static int IDX_CCF_CRITICAL = 4;
    final static int IDX_CCF_PKTID = 5;
    final static int IDX_CCF_TYPE = 6;
    final static int IDX_CCF_STYPE = 7;
    final static int IDX_CCF_APID = 8;
    final static int IDX_CCF_NPARS = 9;
    final static int IDX_CCF_PLAN = 10;
    final static int IDX_CCF_EXEC = 11;
    final static int IDX_CCF_ILSCOPE = 12;
    final static int IDX_CCF_ILSTAGE = 13;
    final static int IDX_CCF_SUBSYS = 14;
    final static int IDX_CCF_HIPRI = 15;
    final static int IDX_CCF_MAPID = 16;
    final static int IDX_CCF_DEFSET = 17;
    final static int IDX_CCF_RAPID = 18;
    final static int IDX_CCF_ACK = 19;
    final static int IDX_CCF_SUBSCHEDID = 20;

    private void loadCcf() throws DatabaseLoadException {
        switchTo("ccf");
        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_CCF_CNAME, IDX_CCF_DESCR, IDX_CCF_PKTID);
            String cname = line[IDX_CCF_CNAME];
            if (cname.startsWith("BINCMD")) {
                continue;
            }
            String pktid = line[IDX_CCF_PKTID];
            TcHeaderRecord thr = tcHeaderRecords.get(pktid);
            if (thr == null && !"NOHEADER".equals(pktid)) {
                throw new MibLoadException(ctx,
                        "Encountered CCF record referencng unknown CCF_PKID=" + pktid);
            }

            MetaCommand mc = new MetaCommand(cname);
            MetaCommand mc1 = mc;
            MetaCommand abstractMc = null;
            if (hasReadOnlyArguments(cname)) {
                abstractMc = new MetaCommand(cname + "_abstract");
                abstractMc.setAbstract(true);
                mc.setBaseMetaCommand(abstractMc);
                mc1 = abstractMc;
            }
            var descr = line[IDX_CCF_DESCR];
            var descr2 = getString(line, IDX_CCF_DESCR2, null);
            if (conf.generatePusNamespace) {
                mc.setShortDescription(descr2);
            } else {
                mc.setShortDescription(descr);
                mc.setLongDescription(descr2);
            }

            String criticality = getString(line, IDX_CCF_CRITICAL, "N");
            Significance signific = "Y".equalsIgnoreCase(criticality) ? SIGNIF_CRITICAL : SIGNIF_NONE;
            mc.setDefaultSignificance(signific);

            CommandContainer mcContainer = new CommandContainer(mc.getName());
            mc.setCommandContainer(mcContainer);
            if (mc != mc1) {
                CommandContainer mc1Container = new CommandContainer(mc.getName());
                mc1.setCommandContainer(mc1Container);
            }
            int firstArgLocation = 0;

            int type = -1;
            int subType = -1;
            int apid = -1;
            if (thr != null) {
                firstArgLocation = thr.firstArgLocation;
                mc1.setBaseMetaCommand(thr.mc);
                mcContainer.setBaseContainer(thr.mc.getCommandContainer());

                if (thr.apid != null) {
                    apid = getInt(line, IDX_CCF_APID);
                    if (allowApidChange) {
                        Argument apidArg = new Argument(thr.apid.getName());
                        apidArg.setArgumentType(thr.apid.getArgumentType());
                        apidArg.setInitialValue(Long.valueOf(apid));
                        mc1.addArgument(apidArg);
                        var apidEntry = new ArgumentEntry(thr.apidLocation, ReferenceLocationType.CONTAINER_START,
                                apidArg);
                        mc1.getCommandContainer().addEntry(apidEntry);
                    } else {
                        mc1.addArgumentAssignment(new ArgumentAssignment(thr.apid.getName(), Integer.toString(apid)));
                    }
                }
                if (thr.type != null) {
                    type = getInt(line, IDX_CCF_TYPE);
                    mc1.addArgumentAssignment(new ArgumentAssignment(thr.type.getName(), Integer.toString(type)));
                }
                if (thr.subType != null) {
                    subType = getInt(line, IDX_CCF_STYPE);
                    mc1.addArgumentAssignment(new ArgumentAssignment(thr.subType.getName(), Integer.toString(subType)));
                }
                if (thr.ack != null && hasColumn(line, IDX_CCF_ACK)) {
                    int ack = getInt(line, IDX_CCF_ACK);
                    if (allowAckChange) {
                        Argument ackArg = new Argument(thr.ack.getName());
                        ackArg.setInitialValue(Long.valueOf(ack));
                        ackArg.setArgumentType(thr.ack.getArgumentType());
                        mc1.addArgument(ackArg);
                        var ackEntry = new ArgumentEntry(thr.ackLocation, ReferenceLocationType.CONTAINER_START,
                                ackArg);
                        mc1.getCommandContainer().addEntry(ackEntry);
                    } else {
                        mc1.addArgumentAssignment(new ArgumentAssignment(thr.ack.getName(), Integer.toString(ack)));
                    }
                }
            }

            if (conf.generatePusNamespace && type != -1 && subType != -1) {
                mc.addAlias(PUS_NAMESPACE, String.format("TC(%d,%d) %s", type, subType, descr));
            }

            if (apid != -1 && type == 17 && subType == 1) {
                // add PUS(17,2) verifier
                SequenceContainer sc = findContainer(apid, 17, 2);
                if (sc != null) {
                    CommandVerifier cv = new CommandVerifier(Type.CONTAINER, "PUS17_Report",
                            new CheckWindow(0, 15000, TimeWindowIsRelativeToType.COMMAND_RELEASE));
                    cv.setContainerRef(sc);
                    mc.addVerifier(cv);
                }
            }
            String hipri = getString(line, IDX_CCF_HIPRI, "N");
            if ("Y".equalsIgnoreCase(hipri)) {
                int mapId = getInt(line, IDX_CCF_MAPID);
                if (mapId < 0 || mapId > 15) {
                    throw new MibLoadException(ctx, "Invalid CCF_MAPID" + mapId);
                }
                mc.addAncillaryData(new AncillaryData(AncillaryData.KEY_CCSDS_MAP_ID, Integer.toString(mapId)));
            }

            addArguments(abstractMc, mc, firstArgLocation);
            if (abstractMc != null) {
                spaceSystem.addMetaCommand(abstractMc);
            }
            spaceSystem.addMetaCommand(mc);
        }
    }

    SequenceContainer findContainer(int apid, int type, int subType) {
        for (var seq : spaceSystem.getSequenceContainers()) {
            ComparisonList clist = (ComparisonList) seq.getRestrictionCriteria();
            if (clist == null) {
                continue;
            }
            boolean foundApid = false;
            boolean foundType = false;
            boolean foundSubType = false;
            for (var comp : clist.getComparisonList()) {
                if (comp.getRef() instanceof ParameterInstanceRef pir) {
                    Parameter p = pir.getParameter();
                    if (PARA_NAME_APID.equals(p.getName())
                            && comp.getStringValue().equals(Integer.toString(apid))) {
                        foundApid = true;
                    }
                    if (PARA_NAME_PUS_TYPE.equals(p.getName())
                            && comp.getStringValue().equals(Integer.toString(type))) {
                        foundType = true;
                    }
                    if (PARA_NAME_PUS_STYPE.equals(p.getName())
                            && comp.getStringValue().equals(Integer.toString(subType))) {
                        foundSubType = true;
                    }
                }

            }

            if (foundApid && foundType && foundSubType) {
                return seq;
            }
        }

        return null;
    }

    boolean hasReadOnlyArguments(String cname) {
        List<CdfRecord> l = cdfRecords.get(cname);
        if (l == null) {
            return false;
        }
        return l.stream().anyMatch(cdf -> "F".equalsIgnoreCase(cdf.eltype));
    }

    /**
     * add arguments.
     * <p>
     * abstractMc is not null and is the parent of mc in case the command has read-only arguments. The read-only
     * arguments are added to the abstractMc and are fixed by the inheritance argument assignment
     * 
     * @param firstArgLocation
     */
    private void addArguments(MetaCommand abstractMc, MetaCommand mc, int firstArgLocation) {
        int count = 0;
        List<CdfRecord> l = cdfRecords.get(mc.getName());
        if (l == null) {
            log.debug("No arguments for command {}", mc.getName());
            return;
        }
        l.sort((c1, c2) -> {
            return Integer.compare(c1.position, c2.position);
        });
        CommandContainer container = mc.getCommandContainer();
        for (int k = 0; k < l.size(); k++) {
            var cdf = l.get(k);
            SequenceEntry se;
            IntegerValue arraySize = null;
            if (cdf.grpsize > 0) {
                if (k + cdf.grpsize >= l.size()) {
                    throw new MibLoadException(ctx,
                            "CFD group size " + cdf.grpsize + " for " + cdf.cpc.pname
                                    + " is larger than the number of command parameters following it for command "
                                    + mc.getName());
                }
                if (l.subList(k + 1, k + cdf.grpsize + 1).stream().anyMatch(r -> r.grpsize > 0)) {
                    // we can fix this one when we have support for aggregates where one member gives the length of
                    // another member which is an array
                    log.warn(
                            "Command {} has multi-level groups and these are not supported. Generating a single element group.",
                            mc.getName());
                    addFixedSingleGroup(container, cdf);
                    continue;
                }
            }
            String arrayAgrName = null;
            if ("A".equalsIgnoreCase(cdf.eltype)) {
                int sizeInBits = cdf.ellen;
                byte[] binary = StringConverter.hexStringToArray(cdf.value);
                if (cdf.ellen > binary.length * 8) {
                    byte[] tmp = new byte[(sizeInBits >> 3) + 1];
                    System.arraycopy(binary, 0, tmp, tmp.length - binary.length, binary.length);
                    binary = tmp;
                }
                se = new FixedValueEntry(cdf.descr, binary, sizeInBits);
                if (cdf.grpsize > 0) {
                    arrayAgrName = "arg" + (count++);
                    arraySize = new FixedIntegerValue(Long.parseLong(cdf.value, 16));
                }
            } else if ("E".equalsIgnoreCase(cdf.eltype)) {
                Argument arg = createArgument(cdf);
                se = new ArgumentEntry(arg);
                String name = arg.getName();
                if (mc.getArgument(name) != null) {
                    int i = 1;
                    while (mc.getArgument(name + "_" + i) != null) {
                        i++;
                    }
                    arg.setName(name + "_" + i);
                }

                if (cdf.grpsize > 0) {
                    if (cdf.grpsize > 1) {
                        arrayAgrName = arg.getName();
                        arg.setName(arrayAgrName + "_count");
                    }
                    arraySize = new DynamicIntegerValue(new ArgumentInstanceRef(arg));
                }
                mc.addArgument(arg);
            } else if ("F".equalsIgnoreCase(cdf.eltype)) {
                Argument arg = createArgument(cdf);
                se = new ArgumentEntry(arg);
                String name = arg.getName();
                if (abstractMc.getArgument(name) != null) {
                    int i = 1;
                    while (abstractMc.getArgument(name + "_" + i) != null) {
                        i++;
                    }
                    arg.setName(name + "_" + i);
                }
                if (cdf.grpsize > 0) {
                    if (cdf.grpsize > 1) {
                        arrayAgrName = arg.getName();
                        arg.setName(arrayAgrName + "_count");
                    }
                    arraySize = new DynamicIntegerValue(new ArgumentInstanceRef(arg));
                }

                abstractMc.addArgument(arg);
                String value = cdf.value;
                if ("D".equals(cdf.inter)) {
                    value = cdf.cpc.defval;
                }
                if (value == null) {
                    throw new MibLoadException(ctx, "parameter=" + arg.getName()
                            + " does not have a default value (expected due to its eltype=F)");
                }
                mc.addArgumentAssignment(new ArgumentAssignment(arg.getName(), value));

            } else {
                throw new MibLoadException(ctx, "parameter with CDF_ELTYPE=" + cdf.eltype + " not supported");
            }
            if (k == 0) {
                se.setLocation(ReferenceLocationType.CONTAINER_START, firstArgLocation);
            } else {
                se.setLocation(ReferenceLocationType.PREVIOUS_ENTRY, 0);
            }
            container.addEntry(se);

            if (cdf.grpsize == 1) {
                addArgumentGroupEntrySingleElementArray(mc, container, "arg" + (count++),
                        l.get(k + 1), arraySize);
                k++;
            } else if (cdf.grpsize > 1) {
                addArgumentGroupEntryAggregate(mc, container, arrayAgrName, l.subList(k + 1, k + cdf.grpsize + 1),
                        arraySize);
                k += cdf.grpsize;
            }
        }

    }

    /**
     * This is called when encountering command arguments group inside group.
     * <p>
     * Argument groups are handled as arrays, see {@link #addArgumentGroupEntry}. Because arrays inside arrays are not
     * supported, we fix the size of all the groups except the innermost one to 1.
     * <p>
     * This method generates a fixed entry with the size of 1
     */
    private void addFixedSingleGroup(CommandContainer container, CdfRecord cdf) {
        int sizeInBits;
        if ("A".equalsIgnoreCase(cdf.eltype)) {
            sizeInBits = cdf.ellen;
        } else if ("E".equalsIgnoreCase(cdf.eltype) || "F".equalsIgnoreCase(cdf.eltype)) {
            CpcRecord cpc = cdf.cpc;
            sizeInBits = MibLoaderBits.sizeInBits(cpc.pfc, cpc.ptc);
        } else {
            throw new MibLoadException(ctx, "parameter with CDF_ELTYPE=" + cdf.eltype + " not supported");
        }

        byte[] binary = new byte[] { 1 };
        if (cdf.ellen > binary.length * 8) {
            byte[] tmp = new byte[(sizeInBits >> 3) + 1];
            System.arraycopy(binary, 0, tmp, tmp.length - binary.length, binary.length);
            binary = tmp;
        }
        container.addEntry(new FixedValueEntry(cdf.descr, binary, sizeInBits));
    }

    private void addArgumentGroupEntryAggregate(MetaCommand mc, CommandContainer container,
            String argName, List<CdfRecord> cdfList, IntegerValue arraySize) {
        AggregateArgumentType.Builder builder = new AggregateArgumentType.Builder();
        int count = 0;
        for (var cdf : cdfList) {
            Member m;
            if ("A".equalsIgnoreCase(cdf.eltype)) {
                IntegerArgumentType idt = new IntegerArgumentType.Builder().setSizeInBits(cdf.ellen)
                        .setInitialValue(cdf.value)
                        .build();
                m = new Member("FIXED" + (count++), idt);
            } else if ("E".equalsIgnoreCase(cdf.eltype) || "F".equalsIgnoreCase(cdf.eltype)) {
                Argument arg = createArgument(cdf);
                m = new Member(arg.getName(), arg.getArgumentType());
                m.setShortDescription(arg.getShortDescription());
            } else {
                throw new MibLoadException(ctx, "parameter with CDF_ELTYPE=" + cdf.eltype + " not supported");
            }
            builder.addMember(m);
        }
        var aggrType = builder.build();
        ArrayArgumentType arrtype = new ArrayArgumentType.Builder().setElementType(aggrType)
                .setSize(Arrays.asList(arraySize))
                .build();
        Argument arg = new Argument(argName);
        arg.setArgumentType(arrtype);
        mc.addArgument(arg);
        container.addEntry(new ArgumentEntry(arg));
    }

    private void addArgumentGroupEntrySingleElementArray(MetaCommand mc, CommandContainer container,
            String argName, CdfRecord cdf, IntegerValue arraySize) {

        ArgumentType atype;
        if ("A".equalsIgnoreCase(cdf.eltype)) {
            atype = new IntegerArgumentType.Builder().setSizeInBits(cdf.ellen)
                    .setInitialValue(cdf.value)
                    .build();

        } else if ("E".equalsIgnoreCase(cdf.eltype) || "F".equalsIgnoreCase(cdf.eltype)) {
            Argument arg = createArgument(cdf);
            atype = arg.getArgumentType();
            argName = cdf.cpc.pname;
        } else {
            throw new MibLoadException(ctx, "parameter with CDF_ELTYPE=" + cdf.eltype + " not supported");
        }

        ArrayArgumentType arrtype = new ArrayArgumentType.Builder().setElementType(atype)
                .setSize(Arrays.asList(arraySize))
                .build();
        Argument arg = new Argument(argName);
        arg.setArgumentType(arrtype);
        if (cdf.cpc != null) {
            arg.setShortDescription(cdf.cpc.descr);
        }
        mc.addArgument(arg);

        container.addEntry(new ArgumentEntry(arg));
    }

    private Argument createArgument(CdfRecord cdf) {
        CpcRecord cpc = cdf.cpc;
        Argument arg = new Argument(cpc.pname);
        arg.setShortDescription(cpc.descr);
        ArgumentType.Builder<?> argType;

        if ("C".equals(cpc.categ)) {
            if (cpc.ccaref == null) {
                throw new MibLoadException(ctx, "For parameter '" + cpc.pname + " CPC_CCAREF was not specified");
            }
            argType = createArgumentTypeCcateg(cdf);
        } else if ("T".equals(cpc.categ)) {
            argType = createArgumentTypeTcateg(cdf);
        } else if ("N".equals(cpc.categ)) {
            argType = createArgumentTypeNcateg(cdf);
        } else if ("P".equals(cpc.categ)) {
            argType = createParameterIdArgumentType(cdf);
        } else if ("A".equals(cpc.categ)) {
            argType = createArgumentTypeAcateg(cdf);
        } else {
            throw new MibLoadException(ctx,
                    "argument '" + cpc.pname + ": CPC_CATEG=" + cpc.categ + " not supported");
        }
        if (cpc.unit != null && cpc.ptc != 12) {// we exclude 12 because aggregate arguments cannot have units
            ((BaseDataType.Builder<?>) argType).addUnit(new UnitType(cpc.unit));
        }
        arg.setArgumentType(argType.build());
        return arg;
    }

    private ArgumentType.Builder<?> createArgumentTypeCcateg(CdfRecord cdf) {
        CpcRecord cpc = cdf.cpc;
        CcaRecord cca = ccaRecords.get(cpc.ccaref);
        if (cca == null) {
            throw new MibLoadException(ctx, "Parameter '" + cpc.pname + " refers to CPC_CCAREF='" + cpc.ccaref
                    + "' not found in the CCA table");
        }
        DataEncoding.Builder<?> encoding = getDataEncoding(ctx, cpc.ptc, cpc.pfc, cdf.vplb);
        encoding.setDefaultCalibrator(new SplineCalibrator(cca.splines));

        if ("R".equals(cca.engfmt)) {
            FloatArgumentType.Builder argType = new FloatArgumentType.Builder().setName(cpc.pname);
            argType.setEncoding(encoding);
            return argType;
        } else if ("I".equals(cca.engfmt)) {
            IntegerArgumentType.Builder argType = new IntegerArgumentType.Builder().setName(cpc.pname);
            argType.setSigned(true);
            argType.setEncoding(encoding);
            return argType;
        } else if ("U".equals(cca.engfmt)) {
            IntegerArgumentType.Builder argType = new IntegerArgumentType.Builder().setName(cpc.pname);
            argType.setSigned(false);
            argType.setEncoding(encoding);
            return argType;
        } else {
            throw new MibLoadException(ctx, "Parameter '" + cpc.pname + " refers to CCA_ENGFMT='" + cca.engfmt
                    + "' unknonw");
        }
    }

    private ArgumentType.Builder<?> createParameterIdArgumentType(CdfRecord cdf) {
        CpcRecord cpc = cdf.cpc;
        if (cpc.ptc != 3) {
            throw new MibLoadException(ctx,
                    "Parameter '" + cpc.pname + " of categ 'P' should have CPC_PTC=4 instead of " + cpc.ptc);
        }
        EnumeratedArgumentType.Builder type = parameterIdArgs.get(cpc.pfc);
        if (type == null) {
            DataEncoding.Builder<?> encoding = getDataEncoding(ctx, cpc.ptc, cpc.pfc, cdf.vplb);
            type = new EnumeratedArgumentType.Builder().setName("parameter_id_" + cpc.pfc);
            for (MibParameter mp : parameters.values()) {
                if (mp.pcf.pid != -1) {
                    type.addEnumerationValue(new ValueEnumeration(mp.pcf.pid, mp.name()));
                }
            }
            type.setEncoding(encoding);
            parameterIdArgs.put(cpc.pfc, type);
        }
        return type;
    }

    private ArgumentType.Builder<?> createArgumentTypeAcateg(CdfRecord cdf) {
        CpcRecord cpc = cdf.cpc;
        BinaryArgumentType.Builder bat = new BinaryArgumentType.Builder()
                .setName(cpc.pname)
                .setEncoding(getDataEncoding(ctx, cpc.ptc, cpc.pfc, cdf.vplb));

        return bat;
    }

    private ArgumentType.Builder<?> createArgumentTypeNcateg(CdfRecord cdf) {
        CpcRecord cpc = cdf.cpc;
        DataEncoding.Builder<?> encoding = getDataEncoding(ctx, cpc.ptc, cpc.pfc, cdf.vplb);
        if (cpc.ptc == 9) {
            AbsoluteTimeArgumentType.Builder atype = new AbsoluteTimeArgumentType.Builder();
            ReferenceTime rt = new ReferenceTime(conf.timeEpoch);
            atype.setReferenceTime(rt);
            atype.setEncoding(encoding);
            atype.setScaling(0, 0.001);
            return atype;
        } else if (cpc.ptc == 12) {
            // (pfc, ptc) = (12, 1) is used to embed TC packets inside TM or TC packets.
            // For example PUS(11,10) time-based schedule detail report
            // (pfc, ptc) = (12, 0) is used to embed TM packets inside TM or TC packets.
            // This does not seem used in PUS however we support it
            if (cpc.pfc != 0 && cpc.pfc != 1) {
                throw new MibLoadException(ctx, String.format("Invalid combination (PTC, PFC):"
                        + " (%d, %d)", cpc.ptc, cpc.pfc));
            }

            AggregateArgumentType.Builder aggrb = new AggregateArgumentType.Builder();
            MibLoaderBits.addPtc12Members(spaceSystem, aggrb, cpc.pfc);
            return aggrb;
        } else {
            return (ArgumentType.Builder<?>) getDataType(encoding, "arg_" + cpc.pname, false);
        }
    }

    private ArgumentType.Builder<?> createArgumentTypeTcateg(CdfRecord cdf) {
        CpcRecord cpc = cdf.cpc;
        if (cpc.pafref == null) {
            throw new MibLoadException(ctx, cpc.pname + ": CPC_PAFREF cannot be null for CPC_CATEF=" + cpc.categ);
        }
        DataEncoding.Builder<?> encoding = getDataEncoding(ctx, cpc.ptc, cpc.pfc, cdf.vplb);
        EnumeratedArgumentType.Builder argType;
        if (encoding instanceof IntegerDataEncoding.Builder) {
            List<ValueEnumeration> l = enumerations.get(cpc.pafref);
            if (l == null) {
                throw new MibLoadException(ctx, cpc.pname + ": could not find PAF/PAS records for CPC_PAFREF="
                        + cpc.pafref + " and type integer/unsigned");
            }
            argType = new EnumeratedArgumentType.Builder().setName(cpc.pname);
            for (ValueEnumeration ve : l) {
                argType.addEnumerationValue(ve);
            }
        } else if (encoding instanceof FloatDataEncoding.Builder) {
            List<ValueEnumerationRange> l = enumerationRanges.get(cpc.pafref);
            if (l == null) {
                throw new MibLoadException(ctx,
                        cpc.pname + ": could not find PAF/PAS records for CPC_PAFREF=" + cpc.pafref + " and type real");
            }
            argType = new EnumeratedArgumentType.Builder().setName(cpc.pname);
            for (ValueEnumerationRange ver : l) {
                argType.addEnumerationRange(ver);
            }
        } else {
            throw new MibLoadException(ctx,
                    "Cannot create enumerated argument with " + encoding.getClass() + " encoding");
        }
        argType.setEncoding(encoding);

        return argType;
    }

    // Command parameters: cpc
    final static int IDX_CPC_PNAME = 0;
    final static int IDX_CPC_DESCR = 1;
    final static int IDX_CPC_PTC = 2;
    final static int IDX_CPC_PFC = 3;
    final static int IDX_CPC_DISPFMT = 4;
    final static int IDX_CPC_RADIX = 5;
    final static int IDX_CPC_UNIT = 6;
    final static int IDX_CPC_CATEG = 7;
    final static int IDX_CPC_PRFREF = 8;
    final static int IDX_CPC_CCAREF = 9;
    final static int IDX_CPC_PAFREF = 10;
    final static int IDX_CPC_INTER = 11;
    final static int IDX_CPC_DEFVAL = 12;
    final static int IDX_CPC_CORR = 13;
    final static int IDX_CPC_OBTID = 14;

    private void loadCpc() throws DatabaseLoadException {
        switchTo("cpc");
        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_CPC_PNAME, IDX_CPC_PTC, IDX_CPC_PFC);
            CpcRecord cpc = new CpcRecord();
            cpc.pname = line[IDX_CPC_PNAME];
            cpc.descr = getString(line, IDX_CPC_DESCR, null);
            cpc.pfc = getInt(line, IDX_CPC_PFC);
            cpc.ptc = getInt(line, IDX_CPC_PTC);
            cpc.unit = getString(line, IDX_CPC_UNIT, null);
            cpc.categ = getString(line, IDX_CPC_CATEG, "N");
            cpc.prfref = getString(line, IDX_CPC_PRFREF, null);
            cpc.ccaref = getString(line, IDX_CPC_CCAREF, null);
            cpc.pafref = getString(line, IDX_CPC_PAFREF, null);
            cpc.inter = getString(line, IDX_CPC_INTER, "R");
            cpc.defval = getString(line, IDX_CPC_DEFVAL, null);
            cpc.corr = getString(line, IDX_CPC_DEFVAL, "Y");
            cpcRecords.put(cpc.pname, cpc);
        }
    }

    // Commands definition: cdf
    final static int IDX_CDF_CNAME = 0;
    final static int IDX_CDF_ELTYPE = 1;
    final static int IDX_CDF_DESCR = 2;
    final static int IDX_CDF_ELLEN = 3;
    final static int IDX_CDF_BIT = 4;
    final static int IDX_CDF_GRPSIZE = 5;
    final static int IDX_CDF_PNAME = 6;
    final static int IDX_CDF_INTER = 7;
    final static int IDX_CDF_VALUE = 8;
    final static int IDX_CDF_TMID = 9;

    final static int IDX_CDF_VPLB = 11;

    private void loadCdf() throws DatabaseLoadException {
        switchTo("cdf");
        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_CDF_CNAME, IDX_CDF_ELTYPE, IDX_CDF_ELLEN, IDX_CDF_BIT);
            String cname = line[IDX_CDF_CNAME];
            if (cname.startsWith("BINCMD")) {
                log.info("Ignoring BINCMD command '{}'", cname);
                continue;
            }
            CdfRecord cdf = new CdfRecord();
            cdf.eltype = line[IDX_CDF_ELTYPE];
            cdf.descr = getString(line, IDX_CDF_DESCR, null);
            cdf.ellen = getInt(line, IDX_CDF_ELLEN);
            cdf.position = getInt(line, IDX_CDF_BIT);
            cdf.grpsize = getInt(line, IDX_CDF_GRPSIZE, 0);

            cdf.vplb = getInt(line, IDX_CDF_VPLB, vblParamLengthBytes);

            if (!"A".equalsIgnoreCase(cdf.eltype)) {
                checkMandatory(line, IDX_CDF_PNAME);
                String pname = line[IDX_CDF_PNAME];
                CpcRecord cpc = cpcRecords.get(pname);
                if (cpc == null) {
                    throw new MibLoadException(ctx, "Parameter CDF_PNAME=" + pname + " not found in CPC table");
                }
                cdf.cpc = cpc;
            }
            cdf.inter = getString(line, IDX_CDF_INTER, "R");
            cdf.value = getString(line, IDX_CDF_VALUE, null);
            cdf.tmid = getString(line, IDX_CDF_TMID, null);

            List<CdfRecord> l = cdfRecords.computeIfAbsent(cname, k -> new ArrayList<>());
            l.add(cdf);

        }
    }

    // Numerical (de-)calibration curves: cca
    // Numerical (de-)calibration curves definition: ccs
    final static int IDX_CCA_NUMBR = 0;
    final static int IDX_CCA_DESCR = 1;
    final static int IDX_CCA_ENGFMT = 2;
    final static int IDX_CCA_RAWFMT = 3;
    final static int IDX_CCA_RADIX = 4;
    final static int IDX_CCA_UNIT = 5;
    final static int IDX_CCA_NCURVE = 6;

    final static int IDX_CCS_NUMBR = 0;
    final static int IDX_CCS_XVALS = 1;
    final static int IDX_CCS_YVALS = 2;

    private void loadDecalib() throws DatabaseLoadException {
        switchTo("cca");

        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_CCA_NUMBR);
            CcaRecord cca = new CcaRecord();
            cca.engfmt = line[IDX_CCA_ENGFMT];
            cca.rawfmt = line[IDX_CCA_RAWFMT];
            cca.radix = line[IDX_CCA_RADIX];
            ccaRecords.put(line[IDX_CCA_NUMBR], cca);
        }
        switchTo("ccs");
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_CCS_NUMBR, IDX_CCS_XVALS, IDX_CCS_YVALS);
            String ccsNumbr = line[IDX_CCS_NUMBR];
            CcaRecord cca = ccaRecords.get(ccsNumbr);

            if (cca == null) {
                throw new MibLoadException(ctx,
                        "For CCS_NUMBR '" + ccsNumbr + "', found no record in CCA file");
            }
            SplinePoint sp = new SplinePoint(
                    getDouble(line[IDX_CCS_XVALS], cca.engfmt, cca.radix),
                    getDouble(line[IDX_CCS_YVALS], cca.engfmt, cca.radix));
            cca.splines.add(sp);
        }
    }

    // Textual (de-)calibrations: paf
    final static int IDX_PAF_NUMBR = 0;
    final static int IDX_PAF_DESCR = 1;
    final static int IDX_PAF_RAWFMT = 2;
    final static int IDX_PAF_NALIAS = 3;
    // Textual (de-)calibrations definitions: pas
    final static int IDX_PAS_NUMBR = 0;
    final static int IDX_PAS_ALTXT = 1;
    final static int IDX_PAS_ALVAL = 2;

    private void loadTxtDecalib() throws DatabaseLoadException {
        switchTo("paf");
        String[] line;
        Map<String, String> rawFormats = new HashMap<>();
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_PAF_NUMBR);
            String pafNumbr = line[IDX_PAF_NUMBR];
            String rawfmt = getString(line, IDX_PAF_RAWFMT, "U");
            if ("U".equals(rawfmt) || "R".equals(rawfmt) || "I".equals(rawfmt)) {
                rawFormats.put(pafNumbr, rawfmt);
            } else {
                throw new MibLoadException(ctx, "Invalid PAF_RAWFMT=" + rawfmt + ". Should be R,U or I");
            }
        }
        switchTo("pas");
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_PAS_NUMBR, IDX_PAS_ALTXT, IDX_PAS_ALVAL);
            String pasNumbr = line[IDX_PAS_NUMBR];
            String rawfmt = rawFormats.get(pasNumbr);

            if (rawfmt == null) {
                throw new MibLoadException(ctx, "PAS_NUMBR=" + line[IDX_PAS_NUMBR] + " has no line in the PAF table");
            }
            if ("U".equals(rawfmt)) {
                long x = getLong(line, IDX_PAS_ALVAL);
                List<ValueEnumeration> l = enumerations.computeIfAbsent(pasNumbr, k -> new ArrayList<>());
                l.add(new ValueEnumeration(x, line[IDX_PAS_ALTXT]));
            } else if ("I".equals(rawfmt)) {
                long x = getUnsignedLong(line, IDX_PAS_ALVAL);
                List<ValueEnumeration> l = enumerations.computeIfAbsent(pasNumbr, k -> new ArrayList<>());
                l.add(new ValueEnumeration(x, line[IDX_PAS_ALTXT]));
            } else if ("R".equals(rawfmt)) {
                double x = getDouble(line, IDX_PAS_ALVAL);
                List<ValueEnumerationRange> l = enumerationRanges.computeIfAbsent(pasNumbr, k -> new ArrayList<>());
                l.add(new ValueEnumerationRange(x, x, true, true, line[IDX_PAS_ALTXT]));
            }
        }
    }

    // Parameter range sets: prf
    final static int IDX_PRF_NUMBR = 0;
    final static int IDX_PRF_DESCR = 1;
    final static int IDX_PRF_INTER = 2;
    final static int IDX_PRF_DSPFMT = 3;
    final static int IDX_PRF_RADIX = 4;
    final static int IDX_PRF_NRANGE = 5;
    final static int IDX_PRF_UNIT = 6;
    // Parameter range values: prv
    final static int IDX_PRV_NUMBR = 0;
    final static int IDX_PRV_MINVAL = 1;
    final static int IDX_PRV_MAXVAL = 2;

    Map<String, PrfRecord> loadPrfPrv() {
        Map<String, PrfRecord> result = new HashMap<>();

        switchTo("prf");
        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_PRF_NUMBR);
            PrfRecord r = new PrfRecord();
            r.descr = getString(line, IDX_PRF_DESCR, null);
            r.inter = getString(line, IDX_PRF_INTER, "R");
            r.inter = getString(line, IDX_PRF_DSPFMT, "U");
            r.inter = getString(line, IDX_PRF_RADIX, "D");
            result.put(line[IDX_PRF_NUMBR], r);
        }
        switchTo("prv");
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_PRV_NUMBR, IDX_PRV_MINVAL);

            PrfRecord r = result.get(line[IDX_PRV_NUMBR]);
            if (r == null) {
                log.warn("Unknown (not defined in prf) PRV_NUMBR found " + line[IDX_PRV_NUMBR]);
                continue;
            }
            String min = getString(line, IDX_PRV_MINVAL, null);
            String max = getString(line, IDX_PRV_MAXVAL, null);
            r.addMinMax(min, max);
        }
        return result;
    }

    // Commands pre-transmission validation: ptv
    final static int IDX_PTV_CNAME = 0;
    final static int IDX_PTV_PARNAM = 1;
    final static int IDX_PTV_INTER = 2;
    final static int IDX_PTV_VAL = 3;

    private void loadPtv() throws DatabaseLoadException {
        Map<String, List<Comparison>> ptvRecords = new HashMap<>();
        switchTo("ptv");
        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_PTV_CNAME, IDX_PTV_PARNAM, IDX_PTV_VAL);
            String cname = line[IDX_PTV_CNAME];
            if (spaceSystem.getMetaCommand(cname) == null) {
                throw new MibLoadException(ctx, "Invalid command PTV_CNAME=" + cname);
            }
            Parameter para = spaceSystem.getParameter(line[IDX_PTV_PARNAM]);
            if (para == null) {
                throw new MibLoadException(ctx, "Reference to invalid parameter PTV_PARNAM=" + line[IDX_PTV_PARNAM]);
            }
            String inter = getString(line, IDX_PTV_INTER, "R");
            boolean useCalibrated = getUseCalibrated("PTV_INTER", inter);

            Comparison c = new Comparison(new ParameterInstanceRef(para, useCalibrated), line[IDX_PTV_VAL],
                    OperatorType.EQUALITY);

            List<Comparison> l = ptvRecords.computeIfAbsent(cname, k -> new ArrayList<>());
            l.add(c);
        }

        for (Map.Entry<String, List<Comparison>> me : ptvRecords.entrySet()) {
            List<Comparison> l = me.getValue();
            MetaCommand mc = spaceSystem.getMetaCommand(me.getKey());
            MatchCriteria criteria;
            if (l.size() == 1) {
                criteria = l.get(0);
            } else {
                ComparisonList cl = new ComparisonList();
                for (Comparison c : l) {
                    cl.addComparison(c);
                }
                criteria = cl;
            }
            mc.addTransmissionConstrain(new TransmissionConstraint(criteria, 0));
        }
    }

    // Verification expressions: cve
    final static int IDX_CVE_CVSID = 0;
    final static int IDX_CVE_PARNAM = 1;
    final static int IDX_CVE_INTER = 2;
    final static int IDX_CVE_VAL = 3;
    final static int IDX_CVE_TOL = 4;
    final static int IDX_CVE_CHECK = 5;

    private Map<String, List<MatchCriteria>> loadCve() {
        Map<String, List<MatchCriteria>> cveRecords = new HashMap<>();
        switchTo("cve");
        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_CVE_CVSID, IDX_CVE_PARNAM);
            String cvsid = line[IDX_CVE_CVSID];
            String parname = line[IDX_CVE_PARNAM];
            Parameter param = spaceSystem.getParameter(parname);
            if (param == null) {
                throw new MibLoadException(ctx, "Found reference to unexisting parameter CVE_PARAM=" + parname);
            }
            String check = getString(line, IDX_CVE_CHECK, "B");
            if ("S".equals(check)) {
                log.debug("Status consistency check not supported, skipping cve verification expression on line"
                        + ctx.lineNum);
                continue;
            }
            checkMandatory(line, IDX_CVE_VAL);
            String inter = getString(line, IDX_CVE_INTER, "R");
            if ("C".equals(inter)) {
                log.debug(
                        "Verification on command parameter not supported, skipping cve verification expression on line"
                                + ctx.lineNum);
                continue;
            }
            boolean useCalibrated = getUseCalibrated("CVE_INTER", inter);

            String tol = getString(line, IDX_CVE_TOL, null);
            MatchCriteria matchCrit = null;
            if (tol != null) {
                ParameterType ptype = param.getParameterType();
                if (useCalibrated) {
                    if (!(ptype instanceof NumericParameterType)) {
                        throw new MibLoadException(ctx,
                                "Cannot use CVE_TOL for a parameter with non numeric eng value" + parname);
                    }
                }
                double tolerance = getDouble(line, IDX_CVE_TOL);
                if (tolerance != 0) {
                    double val = getDouble(line, IDX_CVE_VAL);
                    ComparisonList cl = new ComparisonList();
                    cl.addComparison(new Comparison(new ParameterInstanceRef(param, useCalibrated),
                            Double.toString(val - tolerance), OperatorType.LARGEROREQUALTHAN));
                    cl.addComparison(new Comparison(new ParameterInstanceRef(param, useCalibrated),
                            Double.toString(val + tolerance), OperatorType.SMALLEROREQUALTHAN));
                    matchCrit = cl;
                }
            }
            if (matchCrit == null) {
                matchCrit = new Comparison(new ParameterInstanceRef(param, useCalibrated), line[IDX_CVE_VAL],
                        OperatorType.EQUALITY);
            }
            List<MatchCriteria> l = cveRecords.computeIfAbsent(cvsid, k -> new ArrayList<>());
            l.add(matchCrit);
        }
        return cveRecords;
    }

    // Verification stages file: cvs
    final static int IDX_CVS_ID = 0;
    final static int IDX_CVS_TYPE = 1;
    final static int IDX_CVS_SOURCE = 2;
    final static int IDX_CVS_START = 3;
    final static int IDX_CVS_INTERVAL = 4;
    final static int IDX_CVS_SPID = 5;
    final static int IDX_CVS_UNCERTAINTY = 6;

    private Map<String, CommandVerifierInfo> loadCvs() {
        Map<String, List<MatchCriteria>> paramConditions = loadCve();
        Map<String, CommandVerifierInfo> verifiers = new HashMap<>();
        switchTo("cvs");
        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_CVS_ID, IDX_CVS_TYPE, IDX_CVS_SOURCE, IDX_CVS_START, IDX_CVS_INTERVAL);
            String id = line[IDX_CVS_ID];
            String source = line[IDX_CVS_SOURCE];
            int start = getInt(line, IDX_CVS_START);
            int interval = getInt(line, IDX_CVS_INTERVAL);
            int uncertainty = getInt(line, IDX_CVS_UNCERTAINTY, uncertaintyPeriod);
            String type = line[IDX_CVS_TYPE];

            var stop = start + interval + (uncertainty > 0 ? uncertainty : 0);
            CheckWindow checkWindow = new CheckWindow(start * 1000, stop * 1000,
                    TimeWindowIsRelativeToType.COMMAND_RELEASE);

            String stage = switch (type) {
            case "A" -> "Acceptance";
            case "S" -> "Start";
            case "C" -> "Completion";
            default -> "Progress_" + type;
            };

            CommandVerifierInfo cvInfo;
            if ("R".equals(source)) {
                cvInfo = new Pus1VerifierRecord(stage, checkWindow);
            } else if ("V".equals(source)) {
                List<MatchCriteria> l = paramConditions.get(id);
                if (l == null) {
                    throw new MibLoadException(ctx, "No CVE record found for CVS_ID=" + id);
                }
                var cv = new CommandVerifier(Type.MATCH_CRITERIA, type, checkWindow);
                if (l.size() == 1) {
                    cv.setMatchCriteria(l.get(0));
                } else {
                    var compList = new ComparisonList();
                    for (var mc : l) {
                        if (mc instanceof Comparison c) {
                            compList.addComparison(c);
                        } else if (mc instanceof ComparisonList cl) {
                            for (var c : cl.getComparisonList()) {
                                compList.addComparison(c);
                            }
                        }
                    }
                    cv.setMatchCriteria(compList);
                }

                cvInfo = new CommandVerifierRecord(cv);

            } else {
                throw new MibLoadException(ctx,
                        "Invalid value '" + source + "' for CVS_SOURCE. Expected R or V");
            }
            verifiers.put(id, cvInfo);
        }
        return verifiers;
    }

    private CommandVerifier createPusVerifier(MetaCommand mc, String stage, CheckWindow checkWindow) {
        CommandVerifier cv = new CommandVerifier(Type.ALGORITHM, stage, checkWindow);
        String suffix;
        CustomAlgorithm alg;

        if (allowApidChange) {
            // we need to make a new algorithm for each command because the APID is part of the command
            alg = null;
            suffix = "_" + mc.getName();
        } else {
            // we can reuse the same algorithm because the APID is apart of the base command
            suffix = "";
            alg = (CustomAlgorithm) spaceSystem.getAlgorithm("PUS_Verifier-" + stage);
        }

        if (alg == null) {
            alg = new CustomAlgorithm("PUS_Verifier-" + stage + suffix);
            alg.setScope(Scope.COMMAND_VERIFICATION);
            alg.setInputList(
                    Arrays.asList(
                            getAlgoApidInput(mc, "sentApid"),
                            getAlgoCmdHistInput(PusCommandPostprocessor.CCSDS_SEQCOUNT_PARA_NAME,
                                    "sentSeq"),
                            getAlgoParaInput(PARA_NAME_PUS1_APID, "rcvdApid"),
                            getAlgoParaInput(PARA_NAME_PUS1_SEQCOUNT, "rcvdSeq"),
                            getAlgoParaInput(PARA_NAME_PUS_STYPE, "reportSubType")));
            alg.setOutputList(Collections.emptyList());
            int verificationStage = switch (stage) {
            case "Acceptance" -> 1;
            case "Start" -> 3;
            case "Completion" -> 7;
            default -> 5;
            };
            alg.setAlgorithmText("org.yamcs.scos2k.Pus1Verifier({stage: " + verificationStage + "})");
            alg.setLanguage("java");
            spaceSystem.addAlgorithm(alg);
        }

        cv.setAlgorithm(alg);
        return cv;
    }

    // Verification profiles: cvp
    final static int IDX_CVP_TASK = 0;
    final static int IDX_CVP_TYPE = 1;
    final static int IDX_CVP_CVSID = 2;

    private void loadCvp() {
        Map<String, CommandVerifierInfo> verifiers = loadCvs();
        switchTo("cvp");
        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_CVP_TASK, IDX_CVP_CVSID);
            String type = getString(line, IDX_CVP_TYPE, "C");
            if (!"C".equals(type)) {
                log.debug("Verifiers of CVP_TYPE=" + type + " not supported");
                continue;
            }
            MetaCommand mc = spaceSystem.getMetaCommand(line[IDX_CVP_TASK]);
            if (mc == null) {
                throw new MibLoadException(ctx,
                        "Verifier profile reference to unknown command CVP_TASK=" + line[IDX_CVP_TASK]);
            }
            CommandVerifierInfo cv = verifiers.get(line[IDX_CVP_CVSID]);
            if (cv == null) {
                continue;
                // throw new MibLoadException(ctx,
                // "Verifier profile makes reference to unknown CVP_CVSID=" + line[IDX_CVP_CVSID]);
            }
            if (cv instanceof CommandVerifierRecord cvr) {
                mc.addVerifier(cvr.cmdVerifier);
            } else if (cv instanceof Pus1VerifierRecord pvr) {
                mc.addVerifier(createPusVerifier(mc, pvr.stage, pvr.checkWindow));
            }
        }
    }

    InputParameter getAlgoParaInput(String paraName, String inputName) {
        Parameter p = spaceSystem.getParameter(paraName);
        if (p == null) {
            if (paraName.startsWith(XtceDb.YAMCS_SPACESYSTEM_NAME)) {
                p = new Parameter(NameDescription.getName(paraName));
                p.setQualifiedName(paraName);
            } else {
                throw new MibLoadException("Cannot find parameter " + paraName);
            }
        }
        var ip = new InputParameter(new ParameterInstanceRef(p));
        ip.setInputName(inputName);
        ip.setMandatory(true);

        return ip;
    }

    // find the APID argument of the command sent, required for PUS1 verifier
    InputParameter getAlgoApidInput(MetaCommand cmd, String inputName) {
        MetaCommand cmd1 = cmd;
        Argument apidArg = null;
        outer: while (cmd1 != null) {
            for (var se : cmd1.getCommandContainer().getEntryList()) {
                if (se instanceof ArgumentEntry argEntry) {
                    if (se.getReferenceLocation() != ReferenceLocationType.CONTAINER_START) {
                        continue;
                    }
                    int offset = se.getLocationInContainerInBits();
                    var arg = argEntry.getArgument();
                    var argType = arg.getArgumentType();
                    int size;
                    if (argType instanceof IntegerDataType dt) {
                        size = dt.getEncoding().getSizeInBits();
                    } else if (argType instanceof FloatDataType dt) {
                        size = dt.getEncoding().getSizeInBits();
                    } else if (argType instanceof BooleanDataType dt) {
                        size = dt.getEncoding().getSizeInBits();
                    } else {
                        continue;
                    }
                    if (offset == 5 && size == 11) {
                        apidArg = arg;
                        break outer;
                    }
                    offset += size;
                }
            }
            cmd1 = cmd1.getBaseMetaCommand();
        }

        if (apidArg == null) {
            throw new MibLoadException(
                    "Cannot find APID argument for command " + cmd.getName() + " or its parent");
        }
        System.out.println("found apidArg: " + apidArg);
        ArgumentInstanceRef argInstRef = new ArgumentInstanceRef(apidArg, false);

        return new InputParameter(argInstRef, inputName);
    }

    InputParameter getAlgoCmdHistInput(String name, String inputName) {
        ParameterReference paraRef = new ParameterReference(XtceDb.YAMCS_CMDHIST_SPACESYSTEM_NAME + "/" + name);
        spaceSystem.addUnresolvedReference(paraRef);

        final ParameterInstanceRef parameterInstance = new ParameterInstanceRef();
        paraRef.addResolvedAction(nd -> {
            parameterInstance.setParameter((Parameter) nd);
        });
        return new InputParameter(parameterInstance, inputName);
    }

    interface CommandVerifierInfo {
    }

    record CommandVerifierRecord(CommandVerifier cmdVerifier) implements CommandVerifierInfo {
    }

    record Pus1VerifierRecord(String stage, CheckWindow checkWindow) implements CommandVerifierInfo {
    }
}
