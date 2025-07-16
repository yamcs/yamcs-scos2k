package org.yamcs.scos2k;

import static org.yamcs.scos2k.MibLoaderBits.*;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.scos2k.MibLoaderBits.MibLoadException;
import org.yamcs.scos2k.MonitoringData.DeducedParameter;
import org.yamcs.mdb.AbstractFileLoader;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.BinaryArgumentType;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.BinaryDataType;
import org.yamcs.xtce.BinaryParameterType;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.mdb.DatabaseLoadException;
import org.yamcs.xtce.FloatArgumentType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatDataType;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerArgumentType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerDataType;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.StringArgumentType;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.StringDataType;
import org.yamcs.xtce.StringParameterType;
import org.yamcs.xtce.TimeEpoch;
import org.yamcs.xtce.IntegerDataEncoding.Encoding;
import org.yamcs.xtce.TimeEpoch.CommonEpochs;

public abstract class BaseMibLoader extends AbstractFileLoader {
    // used to generate aliases of shape TC(20,1)
    static final String PUS_NAMESPACE = "PUS";
    // used to generate onboard parameter ids
    static final String OB_PID_NAMESPACE = "OB:PID";
    static final Pattern MIB_PNAME = Pattern.compile("\\w+");

    private List<MibLoadException> errors = new ArrayList<>();
    MibConfig conf = new MibConfig();

    protected MibLoaderContext ctx = new MibLoaderContext(conf, null, -1);
    protected LineNumberReader reader;
    String currentFile;
    String separator = "\t";

    Map<Long, SequenceContainer> spidToSeqContainer = new HashMap<>();
    Map<String, DeducedParameter> deducedParameters = new HashMap<>();

    // PCF_PNAME -> MibParameter
    protected Map<String, MibParameter> parameters = new HashMap<>();

    int counter = 0;
    SpaceSystem spaceSystem;

    String ssName;

    public BaseMibLoader(YConfiguration config) throws ConfigurationException {
        super(config.getString("path"));

        conf.generatePusNamespace = config.getBoolean("generatePusNamespace", true);
        ssName = config.getString("spaceSystemName", "MIB");
        String epoch = config.getString("epoch", "1970-01-01T00:00:00");
        if ("TCO".equalsIgnoreCase(epoch)) {
            conf.tcoService = config.getString("tcoService");
            conf.tcoFineBytes = config.getInt("tcoFineBytes", -1);
        } else {
            try {
                CommonEpochs ce = CommonEpochs.valueOf(epoch.toUpperCase());
                conf.timeEpoch = new TimeEpoch(ce);
            } catch (IllegalArgumentException e) {
                conf.timeEpoch = new TimeEpoch(epoch);// we assume it's a datetime
            }
        }
    }

    protected boolean hasColumn(String[] line, int colNum) {
        if (line.length <= colNum)
            return false;

        String s = line[colNum];
        return s != null && !s.isEmpty();
    }

    protected int getInt(String[] line, int colNum) throws MibLoadException {
        checkMandatory(line, colNum);
        String s = line[colNum];
        try {
            return Integer.parseInt(s);
        } catch (IllegalArgumentException e) {
            throw new MibLoadException(ctx,
                    "Failed to parse '" + s + "' into a number");
        }
    }

    protected int getInt(String[] line, int colNum, int defaultValue) throws MibLoadException {
        if (hasColumn(line, colNum)) {
            return getInt(line, colNum);
        } else {
            return defaultValue;
        }
    }

    protected String getString(String[] line, int colNum, String defaultValue) throws MibLoadException {
        if (line.length > colNum) {
            if (line[colNum].isEmpty()) {
                return defaultValue;
            } else {
                return line[colNum];
            }
        } else {
            return defaultValue;
        }
    }

    protected long getLong(String[] line, int colNum) throws MibLoadException {
        String s = line[colNum];
        try {
            return Long.parseLong(s);
        } catch (IllegalArgumentException e) {
            throw new MibLoadException(ctx,
                    "Failed to parse '" + s + "' into a number");
        }
    }

    protected long getLong(String[] line, int colNum, long defaultValue) throws MibLoadException {
        if (hasColumn(line, colNum)) {
            return getLong(line, colNum);
        } else {
            return defaultValue;
        }
    }

    protected long getUnsignedLong(String[] line, int colNum) throws MibLoadException {
        String s = line[colNum];
        try {
            return Long.parseUnsignedLong(s);
        } catch (IllegalArgumentException e) {
            throw new MibLoadException(ctx,
                    "Failed to parse '" + s + "' into a number");
        }
    }

    protected double getDouble(String[] line, int colNum) throws MibLoadException {
        String s = line[colNum];
        try {
            return Double.parseDouble(s);
        } catch (IllegalArgumentException e) {
            throw new MibLoadException(ctx,
                    "Failed to parse '" + s + "' into a number");
        }
    }

    protected double getDouble(String[] line, int colNum, double defaultValue) throws MibLoadException {
        if (hasColumn(line, colNum)) {
            return getDouble(line, colNum);
        } else {
            return defaultValue;
        }
    }

    protected void checkMandatory(String[] line, int... notnull) {
        for (int k : notnull) {
            if (line.length <= k || line[k] == null) {
                throw new MibLoadException(ctx, "Missing column " + (k + 1));
            }
        }
    }

    protected String[] nextLine() throws DatabaseLoadException {
        try {
            String line = reader.readLine();
            ctx.lineNum = reader.getLineNumber();
            if (ctx.lineNum == 1 && line != null && line.startsWith("#")) {
                line = reader.readLine();
                ctx.lineNum = reader.getLineNumber();
            }
            if (line == null) {
                return null;
            }
            return line.split(separator);
        } catch (IOException e) {
            throw new DatabaseLoadException("Error reading from " + currentFile + ": " + e.getMessage(), e);
        }
    }

    protected void switchTo(String filename) throws DatabaseLoadException {
        currentFile = path + "/" + filename + ".dat";
        try {
            if (reader != null) {
                reader.close();
            }
            reader = new LineNumberReader(new FileReader(currentFile));
            ctx.filename = currentFile;
        } catch (FileNotFoundException e) {
            throw new DatabaseLoadException("Cannot find " + currentFile);
        } catch (IOException e) {
            throw new DatabaseLoadException("Error when closing file", e);
        }
    }

    // make the natural datatype for this encoding knowing there is no calibration
    BaseDataType.Builder<?> getDataType(DataEncoding.Builder<?> encoding, String name, boolean para) {
        if (encoding instanceof IntegerDataEncoding.Builder) {
            IntegerDataType.Builder<?> dtype;
            dtype = para ? new IntegerParameterType.Builder() : new IntegerArgumentType.Builder();
            dtype.setName(name);
            dtype.setSigned(((IntegerDataEncoding.Builder) encoding).getEncoding() != Encoding.UNSIGNED);
            dtype.setSizeInBits(encoding.getSizeInBits() > 32 ? 64 : 32);
            dtype.setEncoding(encoding);
            return dtype;
        } else if (encoding instanceof FloatDataEncoding.Builder) {
            FloatDataType.Builder<?> dtype = para ? new FloatParameterType.Builder().setName(name)
                    : new FloatArgumentType.Builder().setName(name);
            FloatDataEncoding.Builder fde = (FloatDataEncoding.Builder) encoding;
            if (fde.getFloatEncoding() == org.yamcs.xtce.FloatDataEncoding.Encoding.IEEE754_1985) {
                dtype.setSizeInBits(fde.getSizeInBits());
            } else if (fde.getFloatEncoding() == org.yamcs.xtce.FloatDataEncoding.Encoding.MILSTD_1750A) {
                dtype.setSizeInBits(64);
            }
            dtype.setEncoding(encoding);
            return dtype;
        } else if (encoding instanceof BinaryDataEncoding.Builder) {
            BinaryDataType.Builder<?> dtype = para ? new BinaryParameterType.Builder().setName(name)
                    : new BinaryArgumentType.Builder().setName(name);
            dtype.setEncoding(encoding);
            return dtype;
        } else if (encoding instanceof StringDataEncoding.Builder) {
            StringDataType.Builder<?> dtype = para ? new StringParameterType.Builder().setName(name)
                    : new StringArgumentType.Builder().setName(name);
            dtype.setEncoding(encoding);
            return dtype;
        } else {
            throw new IllegalStateException("name:" + name + " encoding: " + encoding);
        }
    }

    protected double getDouble(String val, String fmt, String radix) {
        if ("U".equals(fmt)) {
            int r = 10;
            if ("H".equals(radix)) {
                r = 16;
            } else if ("O".equals(radix)) {
                r = 8;
            }
            return Long.parseLong(val, r);
        } else {
            return Double.parseDouble(val);
        }
    }

    protected boolean getUseCalibrated(String colName, String v) {
        if ("E".equals(v)) {
            return true;
        } else if ("R".equals(v)) {
            return false;
        } else {
            throw new MibLoadException(ctx, "Invalid value '" + v + "'for column " + colName + "; expected E or R");
        }
    }

    void error(MibLoadException e) {
        if (conf.strict) {
            throw e;
        }
        log.warn("{}", e.getMessage());
        errors.add(e);
    }
}
