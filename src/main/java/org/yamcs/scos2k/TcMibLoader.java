package org.yamcs.scos2k;

import static org.yamcs.scos2k.MibLoaderBits.getDataEncoding;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.scos2k.MibLoaderBits.CcaRecord;
import org.yamcs.scos2k.MibLoaderBits.CdfRecord;
import org.yamcs.scos2k.MibLoaderBits.CpcRecord;
import org.yamcs.scos2k.MibLoaderBits.MibLoadException;
import org.yamcs.scos2k.MibLoaderBits.PrfRecord;
import org.yamcs.scos2k.MibLoaderBits.TcHeaderRecord;
import org.yamcs.utils.StringConverter;
import org.yamcs.xtce.Argument;
import org.yamcs.xtce.ArgumentAssignment;
import org.yamcs.xtce.ArgumentEntry;
import org.yamcs.xtce.ArgumentType;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.BinaryArgumentType;
import org.yamcs.xtce.CheckWindow;
import org.yamcs.xtce.CheckWindow.TimeWindowIsRelativeToType;
import org.yamcs.xtce.CommandContainer;
import org.yamcs.xtce.CommandVerifier;
import org.yamcs.xtce.CommandVerifier.Type;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.mdb.DatabaseLoadException;
import org.yamcs.xtce.EnumeratedArgumentType;
import org.yamcs.xtce.FixedValueEntry;
import org.yamcs.xtce.FloatArgumentType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.IntegerArgumentType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.IntegerDataEncoding.Encoding;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.NumericDataEncoding;
import org.yamcs.xtce.NumericParameterType;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtce.SequenceEntry.ReferenceLocationType;
import org.yamcs.xtce.Significance;
import org.yamcs.xtce.Significance.Levels;
import org.yamcs.xtce.SplineCalibrator;
import org.yamcs.xtce.SplinePoint;
import org.yamcs.xtce.TransmissionConstraint;
import org.yamcs.xtce.UnitType;
import org.yamcs.xtce.ValueEnumeration;
import org.yamcs.xtce.ValueEnumerationRange;

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

    private Map<String, TcHeaderRecord> tcHeaderRecords = new HashMap<>();
    Map<String, Argument> headerArgs = new HashMap<>();

    private int uncertaintyPeriod;

    public TcMibLoader(YConfiguration config) throws ConfigurationException {
        super(config);
        YConfiguration tcConf = config.getConfig("TC");
        vblParamLengthBytes = tcConf.getInt("vblParamLengthBytes", 1);
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
            int sizeInBits = getInt(line, IDX_PCDF_LEN);
            CommandContainer container = thr.mc.getCommandContainer();
            String type = line[IDX_PCDF_TYPE];
            Argument arg = null;

            if ("F".equals(type)) {
                checkMandatory(line, IDX_PCDF_VALUE);
                byte[] binary = StringConverter.hexStringToArray(line[IDX_PCDF_VALUE]);
                if (sizeInBits > binary.length * 8) {
                    byte[] tmp = new byte[(sizeInBits >> 3) + 1];
                    System.arraycopy(binary, 0, tmp, tmp.length - binary.length, binary.length);
                    binary = tmp;
                }
                FixedValueEntry fve = new FixedValueEntry(getString(line, IDX_PCDF_DESC, null), binary, sizeInBits);
                fve.setLocation(ReferenceLocationType.CONTAINER_START, getInt(line, IDX_PCDF_BIT));
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
                thr.mc.addArgument(arg);
                ArgumentEntry ae = new ArgumentEntry(arg);
                ae.setLocation(ReferenceLocationType.CONTAINER_START, getInt(line, IDX_PCDF_BIT));
                container.addEntry(ae);

                switch (type) {
                case "A":
                    thr.apid = arg;
                    break;
                case "T":
                    thr.type = arg;
                    break;
                case "S":
                    thr.subType = arg;
                    break;
                case "K":
                    thr.ack = arg;
                    break;
                }
            } else if (!"P".equals(type)) {
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
            mc.setShortDescription(line[IDX_CCF_DESCR]);
            mc.setLongDescription(getString(line, IDX_CCF_DESCR2, null));
            String criticality = getString(line, IDX_CCF_CRITICAL, "N");
            Significance signific = "Y".equalsIgnoreCase(criticality) ? SIGNIF_CRITICAL : SIGNIF_NONE;
            mc.setDefaultSignificance(signific);
            CommandContainer container = new CommandContainer(mc.getName());
            mc.setCommandContainer(container);

            if (thr != null) {
                mc.setBaseMetaCommand(thr.mc);
                container.setBaseContainer(thr.mc.getCommandContainer());
                if (thr.apid != null) {
                    int apid = getInt(line, IDX_CCF_APID);
                    mc.addArgumentAssignment(new ArgumentAssignment(thr.apid.getName(), Integer.toString(apid)));
                }
                if (thr.type != null) {
                    int type = getInt(line, IDX_CCF_TYPE);
                    mc.addArgumentAssignment(new ArgumentAssignment(thr.type.getName(), Integer.toString(type)));
                }
                if (thr.subType != null) {
                    int subType = getInt(line, IDX_CCF_STYPE);
                    mc.addArgumentAssignment(new ArgumentAssignment(thr.subType.getName(), Integer.toString(subType)));
                }
                if (thr.ack != null && hasColumn(line, IDX_CCF_ACK)) {
                    int ack = getInt(line, IDX_CCF_ACK);
                    mc.addArgumentAssignment(new ArgumentAssignment(thr.ack.getName(), Integer.toString(ack)));
                }
            }
            addArguments(mc);
            spaceSystem.addMetaCommand(mc);
        }
    }

    private void addArguments(MetaCommand mc) {

        List<CdfRecord> l = cdfRecords.get(mc.getName());
        if (l == null) {
            log.debug("No arguments for command {}", mc.getName());
            return;
        }
        l.sort((c1, c2) -> {
            return Integer.compare(c1.position, c2.position);
        });
        CommandContainer container = mc.getCommandContainer();
        for (CdfRecord cdf : l) {
            SequenceEntry se;
            if ("A".equalsIgnoreCase(cdf.eltype)) {
                int sizeInBits = cdf.ellen;
                byte[] binary = StringConverter.hexStringToArray(cdf.value);
                if (cdf.ellen > binary.length * 8) {
                    byte[] tmp = new byte[(sizeInBits >> 3) + 1];
                    System.arraycopy(binary, 0, tmp, tmp.length - binary.length, binary.length);
                    binary = tmp;
                }
                se = new FixedValueEntry(cdf.descr, binary, sizeInBits);
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
                mc.addArgument(arg);
            } else if ("F".equalsIgnoreCase(cdf.eltype)) {
                throw new MibLoadException(ctx, "parameter with CDF_ELTYPE=F not supported");
            } else {
                throw new MibLoadException(ctx, "parameter with CDF_ELTYPE=" + cdf.eltype + " not supported");
            }

            se.setLocation(ReferenceLocationType.PREVIOUS_ENTRY, 0);
            container.addEntry(se);
        }
    }

    private Argument createArgument(CdfRecord cdf) {
        CpcRecord cpc = cdf.cpc;
        Argument arg = new Argument(cpc.pname);
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
        if (cpc.unit != null) {
            ((BaseDataType.Builder<?>) argType).addUnit(new UnitType(cpc.unit));
        }
        arg.setArgumentType(argType.build());
        return arg;
    }

    private ArgumentType.Builder<?> createArgumentTypeCcateg(CdfRecord cdf) {
        CpcRecord cpc = cdf.cpc;
        CcaRecord cca = ccaRecords.get(cpc.ccaref);
        if (cca == null) {
            throw new MibLoadException(ctx, "Parameter '" + cpc.pname + " makes reference to CPC_CCAREF='" + cpc.ccaref
                    + "' not found in the CCA table");
        }
        DataEncoding.Builder<?> encoding = getDataEncoding(ctx, cpc.ptc, cpc.pfc, cdf.vplb);
        ((NumericDataEncoding.Builder<?>) encoding).setDefaultCalibrator(new SplineCalibrator(cca.splines));

        if ("R".equals(cca.engfmt)) {
            FloatArgumentType.Builder argType = new FloatArgumentType.Builder().setName(cpc.pname);
            argType.setEncoding(encoding);
            return argType;
        } else if ("I".equals(cca.engfmt)) {
            IntegerArgumentType.Builder argType = new IntegerArgumentType.Builder().setName(cpc.pname);
            argType.setEncoding(encoding);
            return argType;
        } else {
            throw new MibLoadException(ctx, "Parameter '" + cpc.pname + " makes reference to CCA_ENGFMT='" + cca.engfmt
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
                if (mp.pid != -1) {
                    type.addEnumerationValue(new ValueEnumeration(mp.pid, mp.name));
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
        return (ArgumentType.Builder<?>) getDataType(encoding, "arg_" + cpc.pname, false);
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
            cpc.descr = getString(line, IDX_CPC_PNAME, null);
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
                } else {
                    DataEncoding enc = ((BaseDataType) ptype).getEncoding();
                    if (!(enc instanceof NumericDataEncoding)) {
                        throw new MibLoadException(ctx,
                                "Cannot use CVE_TOL for a parameter with non numeric raw value" + parname);
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

    private Map<String, CommandVerifier> loadCvs() {
        Map<String, List<MatchCriteria>> paramConditions = loadCve();
        Map<String, CommandVerifier> verifiers = new HashMap<>();
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
            CheckWindow checkWindow = new CheckWindow(start * 1000, (start + uncertainty) * 1000,
                    TimeWindowIsRelativeToType.COMMAND_RELEASE);

            // TODO : when adding verifiers of type ComparisonList and Comparison we can complete the implementation
            // here
            if ("R".equals(source)) {

            } else if ("V".equals(source)) {
                List<MatchCriteria> l = paramConditions.get(id);
                if (l == null) {
                    throw new MibLoadException(ctx, "No CVE record found for CVS_ID=" + id);
                }
                CommandVerifier v = new CommandVerifier(Type.CONTAINER, type, checkWindow);
            } else {
                throw new MibLoadException(ctx,
                        "Invalid value '" + source + "' for CVS_SOURCE. Expected R or V");
            }
        }
        return verifiers;
    }

    // Verification profiles: cvp
    final static int IDX_CVP_TASK = 0;
    final static int IDX_CVP_TYPE = 1;
    final static int IDX_CVP_CVSID = 2;

    private void loadCvp() {
        Map<String, CommandVerifier> verifiers = loadCvs();
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
            CommandVerifier cv = verifiers.get(line[IDX_CVP_CVSID]);
            if (cv == null) {
                continue;
              //  throw new MibLoadException(ctx,
               //         "Verifier profile makes reference to unknown CVP_CVSID=" + line[IDX_CVP_CVSID]);
            }
            mc.addVerifier(cv);
        }
    }

}
