package org.yamcs.scos2k;

import static org.yamcs.scos2k.MibLoaderBits.*;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.codehaus.commons.compiler.CompileException;
import org.codehaus.janino.SimpleCompiler;
import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.scos2k.MonitoringData.CafRecord;
import org.yamcs.scos2k.MonitoringData.CurRecord;
import org.yamcs.scos2k.MonitoringData.DeducedParameter;
import org.yamcs.scos2k.MibLoaderBits.MibLoadException;
import org.yamcs.scos2k.MonitoringData.OcpRecord;
import org.yamcs.scos2k.MonitoringData.PicRecord;
import org.yamcs.scos2k.MonitoringData.PidRecord;
import org.yamcs.scos2k.MonitoringData.TpcfRecord;
import org.yamcs.scos2k.MonitoringData.VpdRecord;
import org.yamcs.scos2k.MonitoringData.PcfRecord;
import org.yamcs.scos2k.ol.OLAlgorithmEngine;
import org.yamcs.scos2k.ol.OLParser;
import org.yamcs.scos2k.ol.ParseException;
import org.yamcs.scos2k.ol.TokenMgrError;
import org.yamcs.xtce.util.DoubleRange;
import org.yamcs.xtce.AbsoluteTimeParameterType;
import org.yamcs.xtce.AggregateParameterType;
import org.yamcs.xtce.AlarmLevels;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.BinaryParameterType;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.Calibrator;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.ContainerEntry;
import org.yamcs.xtce.ContextCalibrator;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.mdb.DatabaseLoadException;
import org.yamcs.xtce.DynamicIntegerValue;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FixedIntegerValue;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IndirectParameterRefEntry;
import org.yamcs.xtce.InputParameter;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.JavaExpressionCalibrator;
import org.yamcs.xtce.MatchCriteria;
import org.yamcs.xtce.NumericAlarm;
import org.yamcs.xtce.NumericDataEncoding;
import org.yamcs.xtce.NumericParameterType;
import org.yamcs.xtce.OnParameterUpdateTrigger;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.OutputParameter;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.PolynomialCalibrator;
import org.yamcs.xtce.ReferenceTime;
import org.yamcs.xtce.Repeat;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SequenceEntry;
import org.yamcs.xtce.SplineCalibrator;
import org.yamcs.xtce.SplinePoint;
import org.yamcs.xtce.StringParameterType;
import org.yamcs.xtce.TriggerSetType;
import org.yamcs.xtce.UnitType;
import org.yamcs.xtce.ValueEnumeration;
import org.yamcs.xtce.ValueEnumerationRange;
import org.yamcs.xtce.SequenceEntry.ReferenceLocationType;

public abstract class TmMibLoader extends BaseMibLoader {

    private Map<String, List<ValueEnumerationRange>> enumerationRanges = new HashMap<>();
    private Map<String, List<ValueEnumeration>> enumerations = new HashMap<>();
    Map<String, PolynomialCalibrator> polynoms = new HashMap<>();
    Map<String, Calibrator> logCalibrators = new HashMap<>();
    Map<String, Integer> paddings = new HashMap<>();
    Map<String, SplineCalibrator> splineCalibrators = new HashMap<>();
    Map<String, CafRecord> cafRecords = new HashMap<>();
    Map<String, List<CurRecord>> curRecords = new HashMap<>();
    Map<Long, PidRecord> pidRecords = new HashMap<>();
    Map<Long, List<PidRecord>> pidVpdRecords = new HashMap<>();
    // default size in bytes of the size tag for variable length strings and bytestrings
    private int vblParamLengthBytes = 1;
    // where to extract the type and subType from the packet - in bytes
    int typeOffset = 0;
    int subTypeOffset = 0;

    // where the data starts in the PUS1 packets - used to create the parameters for the command verifiers
    int pus1DataOffset;
    Map<String, PcfRecord> savedSynthenticParams = new HashMap<>();

    public TmMibLoader(YConfiguration config) throws ConfigurationException {
        super(config);

        YConfiguration tmConf = config.getConfig("TM");
        vblParamLengthBytes = tmConf.getInt("vblParamLengthBytes", 1);
        if (vblParamLengthBytes < 1) {
            throw new ConfigurationException("vblParamLengthBytes for TM cannot be less than 1");
        }
        typeOffset = tmConf.getInt("typeOffset");
        subTypeOffset = tmConf.getInt("subTypeOffset");
        pus1DataOffset = tmConf.getInt("pus1DataOffset");

    }

    protected void loadTelemetry() {
        loadTelemetryParams();
        loadTelemetryPackets();
        loadPacketEntries();
        loadLimits();
    }

    final static int IDX_CUR_PNAME = 0;
    final static int IDX_CUR_POS = 1;
    final static int IDX_CUR_RLCHK = 2;
    final static int IDX_CUR_VALPAR = 3;
    final static int IDX_CUR_SELECT = 4;

    /**
     * CUR: Calibration conditional selection
     */
    private void loadCur() throws DatabaseLoadException {
        switchTo("cur");
        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_CUR_PNAME, IDX_CUR_POS, IDX_CUR_RLCHK, IDX_CUR_VALPAR, IDX_CUR_SELECT);
            CurRecord r = new CurRecord();
            r.pos = getInt(line, IDX_CUR_POS);
            r.rlchk = line[IDX_CUR_RLCHK];
            r.valpar = getLong(line, IDX_CUR_VALPAR);
            r.select = line[IDX_CUR_SELECT];
            List<CurRecord> l = curRecords.computeIfAbsent(line[IDX_CUR_PNAME], k -> new ArrayList<>());
            l.add(r);
        }
    }

    final static int IDX_CAF_NUMBR = 0;
    final static int IDX_CAF_DESCR = 1;
    final static int IDX_CAF_ENGFMT = 2;
    final static int IDX_CAF_RAWFMT = 3;
    final static int IDX_CAF_RADIX = 4;
    final static int IDX_CAF_UNIT = 5;
    final static int IDX_CAF_NCURVE = 6;
    final static int IDX_CAP_NUMBR = 0;
    final static int IDX_CAP_XVALS = 1;
    final static int IDX_CAP_YVALS = 2;

    /**
     * Loads point pair calibration
     * <p>
     * CAF: Numerical calibrations
     * <p>
     * CAP: Numerical calibrations definition
     * 
     */
    private void loadCafCap() throws DatabaseLoadException {
        switchTo("caf");

        Map<String, List<SplinePoint>> splines = new HashMap<>();

        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_CAF_NUMBR, IDX_CAF_ENGFMT);
            CafRecord r = new CafRecord();
            r.engfmt = line[IDX_CAF_ENGFMT];
            r.rawfmt = line[IDX_CAF_RAWFMT];
            r.radix = line[IDX_CAF_RADIX];
            cafRecords.put(line[IDX_CAF_NUMBR], r);
        }
        switchTo("cap");
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_CAP_NUMBR, IDX_CAP_XVALS, IDX_CAP_YVALS);
            String capNumber = line[IDX_CAP_NUMBR];
            CafRecord cafrec = cafRecords.get(capNumber);
            if (cafrec == null) {
                throw new MibLoadException(ctx,
                        "For CAP_NUMBER '" + capNumber + "' in CAP file, found no record in CAF file");
            }
            SplinePoint sp = new SplinePoint(
                    getDouble(line[IDX_CAP_XVALS], cafrec.rawfmt, cafrec.radix),
                    getDouble(line[IDX_CAP_YVALS], cafrec.engfmt, cafrec.radix));
            List<SplinePoint> l = splines.computeIfAbsent(capNumber, k -> new ArrayList<>());
            l.add(sp);
        }
        splines.forEach((name, l) -> {
            splineCalibrators.put(name, new SplineCalibrator(l));
        });
    }

    final static int IDX_TXF_NUMBER = 0;
    final static int IDX_TXF_DESCR = 1;
    final static int IDX_TXF_RAWFMT = 2;
    final static int IDX_TXF_NALIAS = 3;
    final static int IDX_TXP_NUMBR = 0;
    final static int IDX_TXP_FROM = 1;
    final static int IDX_TXP_TO = 2;
    final static int IDX_TXP_ALTXT = 3;

    /**
     * TXF: Textual calibrations TXP: Textual calibrations definition
     */
    private void loadTxfTxp() throws DatabaseLoadException {
        switchTo("txf");
        String[] line;
        Map<String, String[]> txf = new HashMap<>();
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_TXF_NUMBER, IDX_TXF_RAWFMT);
            txf.put(line[0], line);
        }

        switchTo("txp");
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_TXP_NUMBR, IDX_TXP_FROM, IDX_TXP_TO);

            String txpNumber = line[IDX_TXP_NUMBR];
            String[] txfline = txf.get(txpNumber);
            if (txfline == null) {
                throw new MibLoadException(ctx,
                        "For TXP_NUMBER '" + txpNumber + "' in TXP file, found no record in TXF file");
            }
            String from = line[IDX_TXP_FROM];
            String to = line[IDX_TXP_TO];
            String rawFmt = txfline[IDX_TXF_RAWFMT];
            // if from = to and format is integer we generate a ValueEnumeration, otherwise we do a range (because that
            // one supports doubles)
            if (from.equals(to) && ("I".equals(rawFmt) || "U".equals(rawFmt))) {
                ValueEnumeration ve = new ValueEnumeration(getLong(line, IDX_TXP_FROM), line[IDX_TXP_ALTXT]);
                List<ValueEnumeration> l = enumerations.computeIfAbsent(txpNumber,
                        k -> new ArrayList<ValueEnumeration>());
                l.add(ve);
            } else {
                ValueEnumerationRange ver = new ValueEnumerationRange(getDouble(line, IDX_TXP_FROM),
                        getDouble(line, IDX_TXP_TO),
                        true, true, line[IDX_TXP_ALTXT]);
                List<ValueEnumerationRange> l = enumerationRanges.computeIfAbsent(txpNumber,
                        k -> new ArrayList<ValueEnumerationRange>());
                l.add(ver);
            }

        }
    }

    // Polynomial calibrations definitions: mcf
    final static int IDX_MCF_IDENT = 0;
    final static int IDX_MCF_DESCR = 1;
    final static int IDX_MCF_POL1 = 2;
    final static int IDX_MCF_POL2 = 3;
    final static int IDX_MCF_POL3 = 4;
    final static int IDX_MCF_POL4 = 5;
    final static int IDX_MCF_POL5 = 6;

    private void loadMcf() throws DatabaseLoadException {
        switchTo("mcf");
        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_MCF_IDENT, IDX_MCF_POL1);
            int n = 2;
            for (int k = 2; k < line.length; k++) {
                if (hasColumn(line, k)) {
                    n = k - 1;
                }
            }
            double[] coefficients = new double[n];
            for (int k = 0; k < n; k++) {
                if (hasColumn(line, k + 2)) {
                    coefficients[k] = getDouble(line, k + 2);
                }
            }
            polynoms.put(line[IDX_MCF_IDENT], new PolynomialCalibrator(coefficients));
        }
    }

    // Logarithmic calibrations definitions: lgf
    final static int IDX_LGF_IDENT = 0;
    final static int IDX_LGF_DESCR = 1;
    final static int IDX_LGF_POL1 = 2;
    final static int IDX_LGF_POL2 = 3;
    final static int IDX_LGF_POL3 = 4;
    final static int IDX_LGF_POL4 = 5;
    final static int IDX_LGF_POL5 = 6;

    /**
     * 
     * LGF: Logarithmic calibrations definitions:
     */
    private void loadLgf() throws DatabaseLoadException {
        switchTo("lgf");
        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_LGF_IDENT, IDX_LGF_POL1);
            StringBuilder sb = new StringBuilder();
            sb.append("org.yamcs.scos2k.LogCalibration.calibrate(v, new double[]{");
            sb.append(getDouble(line, IDX_LGF_POL1));
            sb.append(", ").append(getDouble(line, IDX_LGF_POL2, 0));
            sb.append(", ").append(getDouble(line, IDX_LGF_POL3, 0));
            sb.append(", ").append(getDouble(line, IDX_LGF_POL4, 0));
            sb.append(", ").append(getDouble(line, IDX_LGF_POL5, 0));
            sb.append("})");
            JavaExpressionCalibrator calib = new JavaExpressionCalibrator(sb.toString());
            logCalibrators.put(line[IDX_LGF_IDENT], calib);
        }
    }

    /** Monitoring parameters characteristics: pcf */
    final static int IDX_PCF_PNAME = 0;
    final static int IDX_PCF_DESCR = 1;
    final static int IDX_PCF_PID = 2;
    final static int IDX_PCF_UNIT = 3;
    final static int IDX_PCF_PTC = 4;
    final static int IDX_PCF_PFC = 5;
    final static int IDX_PCF_WIDTH = 6;
    final static int IDX_PCF_VALID = 7;
    final static int IDX_PCF_RELATED = 8;
    final static int IDX_PCF_CATEG = 9;
    final static int IDX_PCF_NATUR = 10;
    final static int IDX_PCF_CURTX = 11;
    final static int IDX_PCF_INTER = 12;
    final static int IDX_PCF_USCON = 13;
    final static int IDX_PCF_DECIM = 14;
    final static int IDX_PCF_PARVAL = 15;
    final static int IDX_PCF_SUBSYS = 16;
    final static int IDX_PCF_VALPAR = 17;
    final static int IDX_PCF_SPTTYPE = 18;
    final static int IDX_PCF_VMASK = 23;
    final static int IDX_PCF_VPLB = 24;
    final static int IDX_PCF_PATCHABLE = 25;

    void loadPcf() {
        switchTo("pcf");
        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_PCF_PNAME, IDX_PCF_PTC, IDX_PCF_PFC, IDX_PCF_CATEG, IDX_PCF_NATUR);

            String pname = line[IDX_PCF_PNAME];
            if (!MIB_PNAME.matcher(pname).matches()) {
                throw new MibLoadException(ctx, "Invalid parameter name '" + pname + "'");
            }

            // TODO setunit
            int ptc = getInt(line, IDX_PCF_PTC);
            int pfc = getInt(line, IDX_PCF_PFC);
            PcfRecord pcf = new PcfRecord(pname, line[IDX_PCF_DESCR], ptc, pfc);
            pcf.width = getInt(line, IDX_PCF_WIDTH, -1);
            // TODO PCF valid
            pcf.categ = line[IDX_PCF_CATEG];
            pcf.curtx = getString(line, IDX_PCF_CURTX, null);
            pcf.natur = getString(line, IDX_PCF_NATUR, null);
            pcf.inter = getString(line, IDX_PCF_INTER, null);
            pcf.vplb = getInt(line, IDX_PCF_VPLB, vblParamLengthBytes);
            pcf.pid = getInt(line, IDX_PCF_PID, -1);
            pcf.unit = getString(line, IDX_PCF_UNIT, null);
            pcf.uscon = "Y".equals(getString(line, IDX_PCF_USCON, null));
            pcf.parval = getString(line, IDX_PCF_PARVAL, null);
            pcf.related = getString(line, IDX_PCF_RELATED, null);
            MibParameter mp = new MibParameter(pcf);

            if (ptc == 13) {
                savedSynthenticParams.put(pname, pcf);
                continue;
            }
            if (ptc == 11) {
                if (!hasColumn(line, IDX_PCF_RELATED)) {
                    throw new MibLoadException(ctx, "PCF_RELATED is not set for deduced parameter '" + pname + "'");
                }
                DeducedParameter p = new DeducedParameter(pname, line[IDX_PCF_RELATED]);
                deducedParameters.put(pname, p);
            } else {
                boolean hasContextCalib = curRecords.containsKey(pcf.name);
                ParameterType ptype;
                if (!hasContextCalib) {
                    ptype = spaceSystem.getParameterType(mp.getTypeName());
                    if (ptype == null) {
                        var ptypeb = createParameterType(mp);
                        ptypeb.setName(mp.getTypeName());
                        ptype = ptypeb.build();
                        spaceSystem.addParameterType(ptype);
                    }
                } else {
                    // do not deduplicate parameter types with contextual calibrators
                    var ptypeb = createParameterType(mp);

                    if (ptypeb == null) {
                        log.warn("No parameter type available for {}, ignoring parameter", pcf.name);
                        continue;
                    }
                    String name = mp.getTypeName();
                    int idx = 0;
                    while (true) {
                        if (spaceSystem.getParameterType(name + "_" + idx) == null) {
                            break;
                        }
                        idx++;
                    }
                    ptypeb.setName(name + "_" + idx);
                    ptype = ptypeb.build();
                    spaceSystem.addParameterType(ptype);
                }
                mp.ptype = ptype;
            }
            parameters.put(pname, mp);
        }
    }

    private void loadSynthetic() throws DatabaseLoadException {
        for (MibParameter mp : parameters.values()) {
            if (mp.isSynthetic()) {
                File f = new File(path + "/synthetic/" + mp.name());
                if (!f.exists()) {
                    throw new DatabaseLoadException("Cannot find synthetic file " + f);
                }
                loadSynthetic(mp, f);
            }
        }
    }

    private void loadSynthetic(MibParameter mp, File f) {
        String code = null;
        try {
            Parameter outpara = spaceSystem.getParameter(mp.name());
            if (outpara == null) {
                throw new MibLoadException(ctx, "Cannot find synthetic parameter '" + mp.name() + " in the XTCE db");
            }
            log.debug("Parsing and compiling {}", f);
            String olCode = new String(Files.readAllBytes(f.toPath()), StandardCharsets.ISO_8859_1);
            OLParser parser = new OLParser(new StringReader(olCode));
            code = parser.generateCodeStandalone(mp.name(), name -> {
                MibParameter mibp = parameters.get(name);
                if (mibp == null) {
                    throw new MibLoadException(
                            "Syntentic parameter " + mp.name() + " refers to unexisting parameter " + name);
                }
                return parameters.get(name).ptype;
            });
            log.debug("For{} got code:\n {}", mp.name(), code);
            SimpleCompiler compiler = new SimpleCompiler();
            compiler.cook(code);
            CustomAlgorithm algo = new CustomAlgorithm(mp.name());
            algo.setLanguage(OLAlgorithmEngine.LANGUAGE_NAME);
            algo.setAlgorithmText(olCode);
            TriggerSetType tst = new TriggerSetType();
            List<InputParameter> inputList = new ArrayList<>();

            for (String pname : parser.getInputParameters()) {
                Parameter para = spaceSystem.getParameter(pname);
                InputParameter ip = new InputParameter(new ParameterInstanceRef(para));
                ip.setMandatory(true);
                inputList.add(ip);
                if (!parser.isNoTrigger(pname)) {
                    tst.addOnParameterUpdateTrigger(new OnParameterUpdateTrigger(para));
                }
            }

            algo.setInputSet(inputList);
            algo.setTriggerSet(tst);
            algo.setOutputList(Arrays.asList(new OutputParameter(outpara)));
            spaceSystem.addAlgorithm(algo);
        } catch (ParseException | TokenMgrError e) {
            throw new DatabaseLoadException("Failed to parse the OL file " + f, e);
        } catch (IOException e) {
            throw new DatabaseLoadException(e);
        } catch (CompileException e) {
            throw new DatabaseLoadException("Failed to compile the generated java code " + code, e);
        }
    }

    protected void loadTelemetryParams() throws DatabaseLoadException {
        loadCur();
        loadCafCap();
        loadTxfTxp();
        loadMcf();
        loadLgf();
        loadPcf();
        for (MibParameter mp : parameters.values()) {
            if (mp.pcf.ptc == 11) { // deduced
                continue;
            }
            String pname = mp.name();
            // TODO PCF valid
            Parameter p = new Parameter(pname);
            p.setShortDescription(mp.pcf.descr);
            p.setParameterType(mp.ptype);
            if (mp.pcf.pid != -1) {
                p.addAlias(OB_PID_NAMESPACE, Integer.toString(mp.pcf.pid));
            }
            spaceSystem.addParameter(p);
        }
        loadSynthetic();
        loadContextCalibrators();
    }

    private void loadContextCalibrators() {
        for (Map.Entry<String, List<CurRecord>> me : curRecords.entrySet()) {
            String name = me.getKey();
            List<CurRecord> crl = me.getValue();

            MibParameter mp = parameters.get(name);
            if (mp == null) {
                throw new MibLoadException(null, "Invalid parameter found in the cur file: CUR_PNAME=" + name);
            }
            List<ContextCalibrator> contextCalibratorList = new ArrayList<>();
            for (CurRecord r : crl) {
                Parameter refp = spaceSystem.getParameter(r.rlchk);
                if (refp == null) {
                    throw new MibLoadException(null, "Invalid parameter specied in the cur file: CUR_RLCHK=" + name);
                }

                MatchCriteria ctx = new Comparison(new ParameterInstanceRef(refp, false), Long.toString(r.valpar),
                        OperatorType.EQUALITY);
                Calibrator calibrator = getNumericCalibrator(r.select);
                ContextCalibrator cc = new ContextCalibrator(ctx, calibrator);
                contextCalibratorList.add(cc);
            }
            ((NumericDataEncoding) mp.getEncoding()).setContextCalibratorList(contextCalibratorList);
        }

    }

    private ParameterType.Builder<?> createParameterType(MibParameter mp) {
        String pcfCateg = mp.pcf.categ;
        ParameterType.Builder<?> ptypeb;
        if (pcfCateg.equals("N")) {
            ptypeb = createParameterTypeNcateg(mp);
        } else if (pcfCateg.equals("S")) {
            ptypeb = createParameterTypeScateg(mp.pcf);
        } else if (pcfCateg.equals("T")) {
            ptypeb = createParameterTypeTcateg(mp.pcf);
        } else {
            throw new MibLoadException(ctx, "Invalid value '" + pcfCateg + "' for column PCF_CATEG");
        }

        if (mp.pcf.unit != null) {
            ((BaseDataType.Builder<?>) ptypeb).addUnit(new UnitType(mp.pcf.unit));
        }

        if ("C".equals(mp.pcf.natur) && mp.pcf.parval != null) {
            try {
                ptypeb.setInitialValue(mp.pcf.parval);
            } catch (IllegalArgumentException e) {
                throw new MibLoadException(null,
                        "Cannot set default value for parameter '" + mp.name() + "': " + e.getMessage());
            }
        }

        return ptypeb;
    }

    private ParameterType.Builder<?> createParameterTypeNcateg(MibParameter mp) {
        int ptc = mp.ptc();
        int pfc = mp.pfc();
        DataEncoding.Builder<?> encoding = getDataEncoding(ctx, ptc, pfc, mp.pcf.vplb);

        boolean hasCalibrator = mp.pcf.curtx != null || curRecords.containsKey(mp.name());
        if (ptc == 1) {// boolean
            if (hasCalibrator) {
                throw new MibLoadException(ctx, "Calibration for boolean parameters not supported");
            }
            BooleanParameterType.Builder ptype = new BooleanParameterType.Builder();
            ptype.setEncoding(encoding);
            return ptype;
        } else if (ptc < 6) {// numeric parameters
            if (!hasCalibrator) {
                return (ParameterType.Builder<?>) getDataType(encoding, mp.getTypeName(), true);
            } else {
                mp.hasCalibrator = true;
                ParameterType.Builder<?> ptype = getCalibratedParameterType(mp);
                ptype.setEncoding(encoding);
                Calibrator calib = getNumericCalibrator(mp.pcf.curtx);
                if (calib != null) {
                    if (encoding instanceof NumericDataEncoding.Builder) {
                        ((NumericDataEncoding.Builder<?>) encoding).setDefaultCalibrator(calib);
                    } else {
                        throw new IllegalStateException();
                    }
                }
                return ptype;
            }
        } else {
            if (hasCalibrator) {
                throw new MibLoadException(ctx, "Cannot specify calibrator for (PTC, PFC): (" + ptc + "," + pfc + ")");
            }

            if ((ptc == 6) || (ptc == 7)) {
                BinaryParameterType.Builder ptype = new BinaryParameterType.Builder()
                        .setEncoding(encoding);
                return ptype;
            } else if (ptc == 8) {
                StringParameterType.Builder ptype = new StringParameterType.Builder()
                        .setName(mp.getTypeName())
                        .setEncoding(encoding);
                return ptype;
            } else if (ptc == 9) {
                AbsoluteTimeParameterType.Builder ptype = new AbsoluteTimeParameterType.Builder()
                        .setName(mp.getTypeName());
                ReferenceTime rt = new ReferenceTime(timeEpoch);
                ptype.setReferenceTime(rt);
                ptype.setEncoding(encoding);
                ptype.setScaling(0, 0.001);
                return ptype;
            } else if (ptc == 10) {
                StringParameterType.Builder ptype = new StringParameterType.Builder()
                        .setName(mp.getTypeName());
                ptype.setEncoding(encoding);
                return ptype;
            } else if (ptc == 12) {
                // (pfc, ptc) = (12, 1) is used to embed TC packets inside TM or TC packets.
                // For example PUS(11,10) time-based schedule detail report
                // (pfc, ptc) = (12, 0) is used to embed TM packets inside TM or TC packets.
                // This does not seem used in PUS however we support it
                if (pfc != 0 && pfc != 1) {
                    throw new MibLoadException(ctx, String.format("Invalid combination (PTC, PFC):"
                            + " (%d, %d)", ptc, pfc));
                }

                AggregateParameterType.Builder aggrb = new AggregateParameterType.Builder();
                aggrb.setName(mp.getTypeName());
                MibLoaderBits.addPtc12Members(spaceSystem, aggrb, mp.pfc());
                return aggrb;
            } else {
                throw new MibLoadException(ctx, "Unsupported (PTC, PFC): (" + ptc + "," + pfc + ")");
            }
        }
    }

    private ParameterType.Builder<?> getCalibratedParameterType(MibParameter mp) {
        CafRecord caf = cafRecords.get(mp.pcf.curtx);

        List<CurRecord> l = curRecords.get(mp.name());

        if (caf == null && l != null) {
            CurRecord cur = l.get(0);
            caf = cafRecords.get(cur.select);
        }

        if (caf == null) {
            return new FloatParameterType.Builder();
        }

        if ("R".equals(caf.engfmt)) {
            return new FloatParameterType.Builder();
        } else if ("I".equals(caf.engfmt)) {
            return new IntegerParameterType.Builder();
        } else if ("U".equals(caf.engfmt)) {
            IntegerParameterType.Builder ptype = new IntegerParameterType.Builder()
                    .setSigned(false);
            return ptype;
        } else {
            throw new MibLoadException(ctx, "Invalid value '" + caf.engfmt + "' specified ");
        }
    }

    private Calibrator getNumericCalibrator(String name) {
        Calibrator calib = polynoms.get(name);
        if (calib == null) {
            calib = splineCalibrators.get(name);
        }
        if (calib == null) {
            calib = logCalibrators.get(name);
        }
        return calib;
    }

    private ParameterType.Builder<?> createParameterTypeScateg(PcfRecord pcf) {
        int ptc = pcf.ptc;
        int pfc = pcf.pfc;
        String pcfCurtx = pcf.curtx;
        if (pcfCurtx == null) {
            error(new MibLoadException(ctx,
                    "PCF_CURTX not defined for parameter " + pcf.name + " with PCF_CATEG=" + pcf.categ));
            DataEncoding.Builder<?> encoding = getDataEncoding(ctx, ptc, pfc, -1);
            IntegerParameterType.Builder ptype = new IntegerParameterType.Builder()
                    .setSigned(ptc == 4)
                    .setEncoding(encoding);
            return ptype;
        }

        List<ValueEnumeration> enumList = enumerations.get(pcfCurtx);
        List<ValueEnumerationRange> enumRangeList = enumerationRanges.get(pcfCurtx);
        DataEncoding.Builder<?> encoding = getDataEncoding(ctx, ptc, pfc, -1);

        if (enumList == null && enumRangeList == null) {
            if (curRecords.containsKey(pcf.name)) {
                log.warn("Parameter {}: textual context calibrators not supported, using integer", pcf.name);
                IntegerParameterType.Builder ptype = new IntegerParameterType.Builder()
                        .setSigned(ptc == 4)
                        .setEncoding(encoding);
                return ptype;
            } else {
                error(new MibLoadException(ctx,
                        "Textual calibration for parameter " + pcf.name + " '" + pcfCurtx
                                + "' not found in TXF file"));

                IntegerParameterType.Builder ptype = new IntegerParameterType.Builder()
                        .setSigned(ptc == 4)
                        .setEncoding(encoding);
                return ptype;
            }
        }
        EnumeratedParameterType.Builder ptype = new EnumeratedParameterType.Builder();
        if (enumList != null) {
            for (ValueEnumeration ve : enumList) {
                ptype.addEnumerationValue(ve);
            }
        }
        if (enumRangeList != null) {
            for (ValueEnumerationRange ver : enumRangeList) {
                ptype.addEnumerationRange(ver);
            }
        }
        ptype.setEncoding(encoding);
        return ptype;

    }

    private ParameterType.Builder<?> createParameterTypeTcateg(PcfRecord pcf) {
        int ptc = pcf.ptc;
        int pfc = pcf.pfc;
        if (ptc != 8) {
            throw new MibLoadException(ctx, String.format("Invalid combination (PTC, PFC, PFC_CATEG):"
                    + " (%d, %d, T)", ptc, pfc));
        }
        StringParameterType.Builder ptype = new StringParameterType.Builder();
        DataEncoding.Builder<?> encoding = getDataEncoding(ctx, ptc, pfc, pcf.vplb);
        ptype.setEncoding(encoding);
        if (pcf.parval != null) {
            ptype.setInitialValue(pcf.parval);
        }
        return ptype;
    }

    // Monitoring checks: ocf
    final static int IDX_OCF_NAME = 0;
    final static int IDX_OCF_NBCHCK = 1;
    final static int IDX_OCF_NBOOL = 2;
    final static int IDX_OCF_INTER = 3;
    final static int IDX_OCF_CODIN = 4;

    private void loadLimits() throws DatabaseLoadException {
        Map<String, List<OcpRecord>> ocpRecords = loadOcp();

        switchTo("ocf");
        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_OCF_NAME, IDX_OCF_NBCHCK, IDX_OCF_NBOOL, IDX_OCF_INTER, IDX_OCF_CODIN);
            String pname = line[IDX_OCF_NAME];
            int nbchck = getInt(line, IDX_OCF_NBCHCK);
            int nbool = getInt(line, IDX_OCF_NBOOL);
            String inter = line[IDX_OCF_INTER];
            String codin = line[IDX_OCF_CODIN];
            MibParameter mp = parameters.get(pname);
            if (mp == null) {
                throw new MibLoadException(ctx, "OCF record without a corresponding PCF for parameter '" + pname + "'");
            }
            if (mp.pcf.uscon && nbool == 1) {
                continue; // status consistency check TODO
            }

            List<OcpRecord> l = ocpRecords.get(pname);
            if (l == null) {
                throw new MibLoadException(ctx, "OCF record without a corresponding OCP for parameter '" + pname + "'");
            }

            if ("R".equals(codin) || "I".equals(codin)) {
                if (mp.ptype instanceof EnumeratedParameterType && "U".equals(inter)) {
                    addEnumeratedAlarm(mp, l, true);
                } else if (mp.ptype instanceof BooleanParameterType) {
                    log.warn("Alarms on boolean parameter {} not supported", mp.name());
                    continue;
                } else if (mp.ptype instanceof NumericParameterType) {
                    addNumericAlarm(mp, l, nbchck);
                } else {
                    throw new MibLoadException(ctx, pname + ": OCF_CODIN = " + codin
                            + " cannot be used for a parameter of type " + mp.ptype.getClass()
                            + "(pcf: " + mp.pcf + ")");
                }
            } else if ("A".equals(codin)) {
                if (!(mp.ptype instanceof EnumeratedParameterType)) {
                    throw new MibLoadException(ctx,
                            pname + ": OCF_CODIN = A cannot be used for a parameter of type " + mp.ptype.getClass());
                }
                addEnumeratedAlarm(mp, l, false);
            } else {
                throw new MibLoadException(ctx,
                        pname + ": illegal value " + codin + " for OCF_CODIN (allowed are: R, A and I");
            }
        }
    }

    private void addEnumeratedAlarm(MibParameter mp, List<OcpRecord> l, boolean raw) {
        Parameter param = spaceSystem.getParameter(mp.name());
        EnumeratedParameterType.Builder ptype = (EnumeratedParameterType.Builder) param.getParameterType().toBuilder();

        OcpRecord prev = null;
        MatchCriteria contextMatch = null;

        for (OcpRecord r : l) {
            if ("D".equals(r.type) || "E".equals(r.type) || "C".equals(r.type)) {
                // ignore delta checks, event generation or status consistency TODO
                log.warn("OCP record {} not supported for {}", r, mp.name());
                continue;
            }

            if (r.rlchk != null) { // context alarm
                if (contextMatch != null) {
                    if (!r.rlchk.equals(prev.rlchk) || r.valpar != prev.valpar) {
                        contextMatch = null;
                    }
                }
                if (contextMatch == null) {
                    Parameter paraRef = spaceSystem.getParameter(r.rlchk);
                    if (paraRef == null) {
                        throw new MibLoadException(ctx, "Unknown parameter '" + r.rlchk + " referenced in the ocp");
                    }
                    contextMatch = new Comparison(new ParameterInstanceRef(paraRef, false), Long.toString(r.valpar),
                            OperatorType.EQUALITY);
                }
            } else {
                contextMatch = null;
            }

            AlarmLevels level = "S".equals(r.type) ? AlarmLevels.WARNING : AlarmLevels.CRITICAL;

            if (raw) {
                long rv;
                try {
                    rv = Long.parseLong(r.lvalu);
                } catch (IllegalArgumentException e) {
                    throw new MibLoadException(ctx,
                            "Failed to parse '" + r.lvalu + "' into a number from OCP record " + r);
                }
                String label = findEnumeration(ptype, rv);
                if (label == null) {
                    log.warn("Cannot find label for raw value {} for enumerated parameter type associated to {}: {}",
                            rv, mp.name(), ptype);
                } else {
                    ptype.addAlarm(contextMatch, label, level);
                }
            } else {
                ptype.addAlarm(contextMatch, r.lvalu, level);
            }
            prev = r;
        }
        param.setParameterType(ptype.build());
    }

    private String findEnumeration(EnumeratedParameterType.Builder ptype, long rv) {
        for (ValueEnumeration ve : ptype.getValueEnumerationList()) {
            if (ve.getValue() == rv) {
                return ve.getLabel();
            }
        }

        for (ValueEnumerationRange range : ptype.getValueEnumerationRangeList()) {
            if (range.isValueInRange(rv)) {
                return range.getLabel();
            }
        }

        return null;
    }

    private void addNumericAlarm(MibParameter mp, List<OcpRecord> l, int minViolations) {
        Parameter param = spaceSystem.getParameter(mp.name());
        NumericParameterType.Builder<?> ptypeb = (NumericParameterType.Builder<?>) param.getParameterType().toBuilder();
        int idx = 1;
        while (true) {
            String name = ptypeb.getName() + "_" + idx;
            if (spaceSystem.getParameterType(name) != null) {
                idx++;
                continue;
            }
            ptypeb.setName(name);
            break;
        }
        OcpRecord prev = null;
        MatchCriteria contextMatch = null;
        for (OcpRecord r : l) {
            if ("D".equals(r.type) || "E".equals(r.type) || "C".equals(r.type)) {
                // ignore delta checks, event generation or status consistency TODO
                log.warn("OCP record {} not supported for {}", r, mp.name());
                continue;
            }
            DoubleRange range = null;
            try {
                double low = r.lvalu == null ? Double.NaN : Double.parseDouble(r.lvalu);
                double high = r.hvalu == null ? Double.NaN : Double.parseDouble(r.hvalu);
                range = new DoubleRange(low, high);
            } catch (IllegalArgumentException e) {
                throw new MibLoadException(ctx, "Cannot parse low/high " + r.lvalu + "/" + r.hvalu
                        + " values from ocp for parameter " + mp.name());
            }

            if (r.rlchk != null) { // context alarm
                if (contextMatch != null) {
                    if (!r.rlchk.equals(prev.rlchk) || r.valpar != prev.valpar) {
                        contextMatch = null;
                    }
                }
                if (contextMatch == null) {
                    Parameter paraRef = spaceSystem.getParameter(r.rlchk);
                    if (paraRef == null) {
                        error(new MibLoadException(ctx, "Unknown parameter '" + r.rlchk + " referenced in the ocp"));
                        continue;
                    }
                    contextMatch = new Comparison(new ParameterInstanceRef(paraRef, false), Long.toString(r.valpar),
                            OperatorType.EQUALITY);
                }
            } else {
                contextMatch = null;
            }

            AlarmLevels level = "S".equals(r.type) ? AlarmLevels.WARNING : AlarmLevels.CRITICAL;
            NumericAlarm alarm = ptypeb.createOrGetAlarm(contextMatch);
            alarm.setMinViolations(minViolations);
            alarm.getStaticAlarmRanges().addRange(range, level);
            prev = r;
        }
        ParameterType ptype = ptypeb.build();
        spaceSystem.addParameterType(ptype);
        param.setParameterType(ptype);
    }

    // Monitoring checks definition: ocp
    final static int IDX_OCP_NAME = 0;
    final static int IDX_OCP_POS = 1;
    final static int IDX_OCP_TYPE = 2;
    final static int IDX_OCP_LVALU = 3;
    final static int IDX_OCP_HVALU = 4;
    final static int IDX_OCP_RLCHK = 5;
    final static int IDX_OCP_VALPAR = 6;

    private Map<String, List<OcpRecord>> loadOcp() throws DatabaseLoadException {
        switchTo("ocp");
        String[] line;
        Map<String, List<OcpRecord>> records = new HashMap<>();
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_OCP_NAME, IDX_OCP_POS, IDX_OCP_TYPE);
            String pname = line[IDX_OCP_NAME];
            OcpRecord r = new OcpRecord();
            r.pos = getInt(line, IDX_OCP_POS);
            r.type = getString(line, IDX_OCP_TYPE, null);
            r.lvalu = getString(line, IDX_OCP_LVALU, null);
            r.hvalu = getString(line, IDX_OCP_HVALU, null);
            r.rlchk = getString(line, IDX_OCP_RLCHK, null);
            r.valpar = getLong(line, IDX_OCP_VALPAR, 1);
            List<OcpRecord> l = records.computeIfAbsent(pname, k -> new ArrayList<>());
            l.add(r);
        }
        for (List<OcpRecord> l : records.values()) {
            l.sort((r1, r2) -> Integer.compare(r1.pos, r2.pos));
        }

        return records;
    }

    // Packets identification criteria: pic
    final static int IDX_PIC_TYPE = 0;
    final static int IDX_PIC_STYPE = 1;
    final static int IDX_PIC_PI1_OFF = 2;
    final static int IDX_PIC_PI1_WID = 3;
    final static int IDX_PIC_PI2_OFF = 4;
    final static int IDX_PIC_PI2_WID = 5;
    final static int IDX_PIC_APID = 6;

    private List<PicRecord> loadPic() throws DatabaseLoadException {
        switchTo("pic");
        List<PicRecord> result = new ArrayList<>();
        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_PIC_TYPE, IDX_PIC_STYPE, IDX_PIC_PI1_OFF, IDX_PIC_PI1_WID, IDX_PIC_PI2_OFF,
                    IDX_PIC_PI2_WID);
            PicRecord pr = new PicRecord();
            pr.type = getInt(line, IDX_PIC_TYPE);
            pr.stype = getInt(line, IDX_PIC_STYPE);
            pr.pi1Offset = getInt(line, IDX_PIC_PI1_OFF);
            pr.pi1Width = getInt(line, IDX_PIC_PI1_WID);
            pr.pi2Offset = getInt(line, IDX_PIC_PI2_OFF);
            pr.pi2Width = getInt(line, IDX_PIC_PI2_WID);
            pr.apid = hasColumn(line, IDX_PIC_APID) ? getInt(line, IDX_PIC_APID) : -1;
            result.add(pr);
        }
        return result;
    }

    // Packets identification: pid
    final static int IDX_PID_TYPE = 0;
    final static int IDX_PID_STYPE = 1;
    final static int IDX_PID_APID = 2;
    final static int IDX_PID_PI1_VAL = 3;
    final static int IDX_PID_PI2_VAL = 4;
    final static int IDX_PID_SPID = 5;
    final static int IDX_PID_DESCR = 6;
    final static int IDX_PID_UNIT = 7;
    final static int IDX_PID_TPSD = 8;
    final static int IDX_PID_DHHSIZE = 9;
    final static int IDX_PID_TIME = 10;
    final static int IDX_PID_INTER = 11;
    final static int IDX_PID_VALID = 12;
    final static int IDX_PID_CHECK = 13;
    final static int IDX_PID_EVENT = 14;
    final static int IDX_PID_EVID = 15;

    void loadPid() throws DatabaseLoadException {
        switchTo("pid");
        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_PID_TYPE, IDX_PID_STYPE, IDX_PID_APID, IDX_PID_SPID);
            if ("N".equals(line[IDX_PID_VALID])) {
                if (log.isDebugEnabled()) {
                    log.debug("Skipping PID line because VALID=N: {}", Arrays.toString(line));
                }
                continue;
            }
            PidRecord pid = new PidRecord();
            pid.type = getInt(line, IDX_PID_TYPE);
            pid.stype = getInt(line, IDX_PID_STYPE);
            pid.apid = getInt(line, IDX_PID_APID);
            if (hasColumn(line, IDX_PID_PI1_VAL)) {
                pid.pi1 = getUnsignedLong(line, IDX_PID_PI1_VAL);
            }
            if (hasColumn(line, IDX_PID_PI2_VAL)) {
                pid.pi2 = getUnsignedLong(line, IDX_PID_PI2_VAL);
            }
            pid.spid = getLong(line, IDX_PID_SPID);
            long tpsd = getLong(line, IDX_PID_TPSD, -1);
            pid.dfhsize = getInt(line, IDX_PID_DHHSIZE);
            if (tpsd != -1) {
                List<PidRecord> l = pidVpdRecords.computeIfAbsent(tpsd, k -> new ArrayList<>());
                l.add(pid);
            }
            pidRecords.put(pid.spid, pid);
        }
    }

    protected void loadTelemetryPackets() {
        List<PicRecord> picRecords = loadPic();
        loadPid();
        Map<Long, TpcfRecord> tpcfRecods = loadTpcf();
        int[] apidtsypes = pidRecords.values().stream().mapToInt(r -> r.getApidTypeSubtype()).distinct().toArray();

        createRootPusContainers(spaceSystem, typeOffset, subTypeOffset);

        for (int apidtstype : apidtsypes) {
            int type = (apidtstype >> 8) & 0xFF;
            int stype = apidtstype & 0xFF;
            int apid = apidtstype >> 16;
            List<PidRecord> l = pidRecords.values().stream().filter(r -> r.getApidTypeSubtype() == apidtstype)
                    .collect(Collectors.toList());
            String name = getContainerName(apid, type, stype);
            SequenceContainer seq;

            if (l.size() == 1) {
                PidRecord pid = l.get(0);
                TpcfRecord tr = tpcfRecods.get(pid.spid);
                if (tr != null && tr.name != null) {
                    name = sanitizeName(tr.name);
                }
                if (spaceSystem.getSequenceContainer(name) != null) {
                    name = name + " spid_" + pid.spid;
                }
                seq = createLevel1Subcontainer(spaceSystem, apid, type, stype, name, pus1DataOffset);
                spidToSeqContainer.put(pid.spid, seq);
            } else {
                PicRecord pic = findPic(picRecords, type, stype, apid);
                if (pic == null) {
                    throw new MibLoadException(ctx,
                            "Cannot find a PIC record for type=" + type + " stype=" + stype + " apid = " + apid);
                }
                seq = createLevel1Subcontainer(spaceSystem, apid, type, stype, name, pus1DataOffset);
                Parameter pi1 = null;
                Parameter pi2 = null;
                if (pic.pi1Offset >= 0) {
                    if (pic.pi1Width > 0) {
                        IntegerParameterType pi1type = getUnsignedParameterType(spaceSystem, pic.pi1Width);
                        pi1 = new Parameter(name + "_pi1");
                        pi1.setParameterType(pi1type);
                        spaceSystem.addParameter(pi1);
                        seq.addEntry(new ParameterEntry(8 * pic.pi1Offset, ReferenceLocationType.CONTAINER_START, pi1));
                    } else {
                        error(new MibLoadException(ctx,
                                "Found PIC record with p1_offset >= 0 and p1_width <= 0: " + pic));
                    }
                }
                if (pic.pi2Offset >= 0) {
                    if (pic.pi1Width > 0) {
                        IntegerParameterType pi2type = getUnsignedParameterType(spaceSystem, pic.pi2Width);
                        pi2 = new Parameter(name + "_pi2");
                        pi2.setParameterType(pi2type);
                        spaceSystem.addParameter(pi2);
                        seq.addEntry(
                                new ParameterEntry(8 * pic.pi2Offset, ReferenceLocationType.CONTAINER_START, pi2));
                    } else {
                        error(new MibLoadException(ctx,
                                "Found PIC record with p2_offset >= 0 and p2_width <= 0: " + pic));
                    }
                }

                for (PidRecord pid : l) {
                    TpcfRecord tr = tpcfRecods.get(pid.spid);
                    if (tr != null && tr.name != null) {
                        name = sanitizeName(tr.name);
                        if (spaceSystem.getSequenceContainer(name) != null) {
                            name = name + " spid_" + pid.spid;
                        }
                    } else {
                        name = name + "spid_" + pid.spid;
                    }
                    SequenceContainer seq1 = new SequenceContainer(name);
                    seq1.setBaseContainer(seq);
                    ComparisonList cl = new ComparisonList();
                    if (pi1 != null) {
                        cl.addComparison(
                                new Comparison(new ParameterInstanceRef(pi1, false), Long.toString(pid.pi1),
                                        OperatorType.EQUALITY));
                    }
                    if (pi2 != null) {
                        cl.addComparison(
                                new Comparison(new ParameterInstanceRef(pi2, false), Long.toString(pid.pi2),
                                        OperatorType.EQUALITY));
                    }
                    seq1.setRestrictionCriteria(cl);
                    spaceSystem.addSequenceContainer(seq1);
                    spidToSeqContainer.put(pid.spid, seq1);
                }
            }
        }
        loadVpd();
    }

    // Telemetry packets characteristics: tpcf
    final static int IDX_TPCF_SPID = 0;
    final static int IDX_TPCF_NAME = 1;
    final static int IDX_TPCF_SIZE = 2;

    private Map<Long, TpcfRecord> loadTpcf() {
        Map<Long, TpcfRecord> records = new HashMap<>();
        switchTo("tpcf");
        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_TPCF_SPID);
            TpcfRecord tr = new TpcfRecord();
            long spid = getLong(line, IDX_TPCF_SPID);
            tr.name = getString(line, IDX_TPCF_NAME, null);
            tr.size = getInt(line, IDX_TPCF_SIZE, -1);
            records.put(spid, tr);
        }

        return records;
    }

    private PicRecord findPic(List<PicRecord> picRecords, int type, int stype, int apid) {
        PicRecord r = null;
        for (PicRecord pic : picRecords) {
            if (pic.type == type && pic.stype == stype) {
                if (pic.apid == apid) {
                    return pic;
                } else if (pic.apid == -1) {
                    r = pic;
                }
            }
        }
        return r;
    }

    // Parameters location in fixed packets: plf
    final static int IDX_PLF_NAME = 0;
    final static int IDX_PLF_SPID = 1;
    final static int IDX_PLF_OFFBY = 2;
    final static int IDX_PLF_OFFBI = 3;
    final static int IDX_PLF_NBOCC = 4;
    final static int IDX_PLF_LGOCC = 5;
    final static int IDX_PLF_TIME = 6;
    final static int IDX_PLF_TDOCC = 7;

    void loadPacketEntries() {
        switchTo("plf"); // PLF: Parameters location in fixed packets
        String[] line;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_PLF_NAME, IDX_PLF_SPID, IDX_PLF_OFFBY, IDX_PLF_OFFBI);
            long spid = getUnsignedLong(line, IDX_PLF_SPID);
            SequenceContainer seq = spidToSeqContainer.get(spid);
            if (seq == null) {
                log.warn("Unknown packet with SPID " + spid + ", ignoring");
                continue;
            }

            String pname = line[IDX_PLF_NAME];

            int locationInBits = getInt(line, IDX_PLF_OFFBY) * 8 + getInt(line, IDX_PLF_OFFBI);
            int nbocc = getInt(line, IDX_PLF_NBOCC, 1);
            if (nbocc < 1) {
                throw new MibLoadException(ctx, "Invalid PLF_NBOCC " + nbocc);
            }
            SequenceEntry entry;
            if (deducedParameters.containsKey(pname)) {
                DeducedParameter dp = deducedParameters.get(pname);
                ParameterInstanceRef ref = new ParameterInstanceRef();
                ref.setParameter(spaceSystem.getParameter(dp.refPname));
                entry = new IndirectParameterRefEntry(locationInBits, ReferenceLocationType.CONTAINER_START, ref,
                        OB_PID_NAMESPACE);
            } else if (savedSynthenticParams.containsKey(pname)) {
                log.debug("Ignoring saved synthetic param {}", pname);
                continue;
            } else {
                Parameter p = spaceSystem.getParameter(pname);
                if (p == null) {
                    throw new MibLoadException(ctx, "Unknown parameter '" + pname + "'");
                }
                entry = new ParameterEntry(locationInBits, ReferenceLocationType.CONTAINER_START, p);
            }

            if (nbocc > 1) {
                Repeat repeat = new Repeat(new FixedIntegerValue(nbocc));
                int lgocc = getInt(line, IDX_PLF_LGOCC, 0);
                repeat.setOffsetSizeInBits(lgocc);
                entry.setRepeatEntry(repeat);
            }
            seq.addEntry(entry);
        }

    }

    // Variable packet definition: vpd
    final static int IDX_VPD_TPSD = 0;
    final static int IDX_VPD_POS = 1;
    final static int IDX_VPD_NAME = 2;
    final static int IDX_VPD_GRPSIZE = 3;
    final static int IDX_VPD_FIXREP = 4;
    final static int IDX_VPD_CHOICE = 5;
    final static int IDX_VPD_PIDREF = 6;
    final static int IDX_VPD_DISDESC = 7;
    final static int IDX_VPD_WIDTH = 8;
    final static int IDX_VPD_JUSTIFY = 9;
    final static int IDX_VPD_NEWLINE = 10;
    final static int IDX_VPD_DCHAR = 11;
    final static int IDX_VPD_FORM = 12;
    final static int IDX_VPD_OFFSET = 13;

    /**
     * Load Variable Packet Display definition
     */
    void loadVpd() {
        switchTo("vpd");
        String[] line;
        Map<Long, List<VpdRecord>> records = new HashMap<>();
        HashSet<Long> skipped = new HashSet<>();
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_VPD_TPSD, IDX_VPD_POS, IDX_VPD_NAME);
            VpdRecord vpd = new VpdRecord();
            long tpsd = getLong(line, IDX_VPD_TPSD);
            vpd.name = line[IDX_VPD_NAME];
            vpd.pos = getInt(line, IDX_VPD_POS);
            vpd.grpSize = getInt(line, IDX_VPD_GRPSIZE, 0);
            vpd.choice = getString(line, IDX_VPD_CHOICE, "N");
            vpd.grpSize = getInt(line, IDX_VPD_GRPSIZE, 0);
            vpd.choice = getString(line, IDX_VPD_CHOICE, "N");
            vpd.offset = getInt(line, IDX_VPD_OFFSET, 0);

            if (!pidVpdRecords.containsKey(tpsd)) {
                if (!skipped.contains(tpsd)) {
                    skipped.add(tpsd);
                    error(new MibLoadException(ctx, "VPD_TPSD=" + tpsd + " has no correspondence in the pid table"));
                }
                continue;
            }

            if (spaceSystem.getParameter(vpd.name) == null && !deducedParameters.containsKey(vpd.name)) {
                throw new MibLoadException(ctx, "VPD_NAME=" + vpd.name + " has no correspondence in the pcf table");
            }

            List<VpdRecord> l = records.computeIfAbsent(tpsd, k -> new ArrayList<>());
            l.add(vpd);
        }
        for (List<VpdRecord> l : records.values()) {
            l.sort((vr1, vr2) -> Integer.compare(vr1.pos, vr2.pos));
        }

        for (Map.Entry<Long, List<VpdRecord>> me : records.entrySet()) {
            long tpsd = me.getKey();
            List<VpdRecord> l = me.getValue();
            for (PidRecord pr : pidVpdRecords.get(tpsd)) {
                SequenceContainer seq = spidToSeqContainer.get(pr.spid);
                addVdpParamsToContainer(seq, 8 * pr.dfhsize, l, 0);
            }
        }
    }

    private void addVdpParamsToContainer(SequenceContainer seq, int absOffset, List<VpdRecord> lvdpRecords, int count) {
        for (int i = 0; i < lvdpRecords.size(); i++) {
            count++;
            VpdRecord vpd = lvdpRecords.get(i);
            Parameter p = spaceSystem.getParameter(vpd.name);
            int offset = vpd.offset;
            ReferenceLocationType location = ReferenceLocationType.PREVIOUS_ENTRY;
            if (count == 1) { // for the first parameter we have to use an absolute offset from the beginning of the
                              // packet
                offset += absOffset;
                location = ReferenceLocationType.CONTAINER_START;
            }
            if (p == null) {
                DeducedParameter dp = deducedParameters.get(vpd.name);
                ParameterInstanceRef ref = new ParameterInstanceRef();
                ref.setParameter(spaceSystem.getParameter(dp.refPname));
                IndirectParameterRefEntry entry = new IndirectParameterRefEntry(offset, location, ref,
                        OB_PID_NAMESPACE);
                seq.addEntry(entry);
            } else {
                ParameterEntry pe = new ParameterEntry(offset, location);
                pe.setParameter(p);
                seq.addEntry(pe);
            }

            if (vpd.grpSize > 0) {
                if (lvdpRecords.size() - i - 1 < vpd.grpSize) {
                    throw new MibLoadException(null,
                            "Inconsistency in vpd file, for parameter " + vpd.name + " on position " + vpd.pos
                                    + " grp size is " + vpd.grpSize + " but there are only " + i
                                    + " positions left until the end of the structure");
                }
                if (vpd.grpSize == 1) {
                    i++;
                    VpdRecord vpd1 = lvdpRecords.get(i);
                    Parameter p1 = spaceSystem.getParameter(vpd1.name);
                    if (p1 == null) {
                        throw new MibLoadException(null,
                                "Cannot find parameter '" + vpd.name + " referenced in vpd table");
                    }
                    ParameterEntry pe1 = new ParameterEntry(vpd1.offset, ReferenceLocationType.PREVIOUS_ENTRY);
                    pe1.setRepeatEntry(new Repeat(new DynamicIntegerValue(new ParameterInstanceRef(p)), 0));
                    pe1.setParameter(p1);
                    seq.addEntry(pe1);
                } else {
                    SequenceContainer seq1 = new SequenceContainer(seq.getName() + "_" + count);
                    ContainerEntry ce = new ContainerEntry(0, ReferenceLocationType.PREVIOUS_ENTRY, seq1);
                    ce.setRepeatEntry(new Repeat(new DynamicIntegerValue(new ParameterInstanceRef(p)), 0));
                    seq.addEntry(ce);
                    addVdpParamsToContainer(seq1, -1, lvdpRecords.subList(i + 1, i + 1 + vpd.grpSize), count);
                    i += vpd.grpSize;
                    spaceSystem.addSequenceContainer(seq1);
                }
            }
        }
    }
    // Monitoring groups definition: grp
    // Parameters groups: grpa
    // Telemetry packets groups: grpk

}
