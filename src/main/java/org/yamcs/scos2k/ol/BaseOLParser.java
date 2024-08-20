package org.yamcs.scos2k.ol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yamcs.xtce.AbsoluteTimeParameterType;
import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.BinaryParameterType;
import org.yamcs.xtce.BooleanDataEncoding;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.EnumeratedParameterType;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.NumericDataEncoding;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.StringParameterType;
import org.yamcs.mdb.DataEncodingDecoder;
import org.yamcs.utils.TaiUtcConverter;
import org.yamcs.utils.TimeEncoding;

/**
 * Transforms OL Formulas into java classes.
 * 
 * @author nm
 *
 */
public abstract class BaseOLParser {
    Map<String, Variable> localVariables = new HashMap<String, Variable>();

    List<String> inputParams = new ArrayList<>();
    Set<String> noTriggerParams = new HashSet<>();// these are the $ parameters - their change do not trigger the
                                                  // computation
    protected StringBuilder body;

    private Function<String, ParameterType> parameterTypes;
    private String name;

    // Some GAIA scripts were giving errors if this was not done
    //
    private boolean defaultValuesForLocalVariables = true;

    /**
     * Generates the import statement required before the class definition
     * 
     * @param code
     */
    public static void generateCodeImports(StringBuilder code) {
        code.append("package org.yamcs.scos2k.ol.generated;\n");
        code.append("import java.util.List;\n");
        code.append("import org.yamcs.parameter.ParameterValue;\n");
        code.append("import org.yamcs.parameter.RawEngValue;\n");
        code.append("import org.yamcs.scos2k.ol.OLEvaluator;\n");
        code.append("import org.yamcs.scos2k.ol.OLFunction;\n");
        code.append("import org.yamcs.scos2k.ol.GlobalVariables;\n");
    }

    /**
     * Generate code that can be used in a java compilation unit
     * 
     * @param name
     *            - the name of the output parameter
     * @param inputParameterTypes
     *            - function that gives the types for the input parameters; the function should return null if the
     *            parameter is unknown (a ParseException will be thrown in this case).
     * @return a piece of java code ready to be compiled
     * @throws ParseException
     */
    public String generateCodeStandalone(String name, Function<String, ParameterType> inputParameterTypes)
            throws ParseException {
        StringBuilder code = new StringBuilder();
        generateCodeImports(code);

        generateCode(name, code, inputParameterTypes);

        return code.toString();
    }

    /**
     * Appends the class definition to the StringBuilder (which needs to have on top the package and import
     * declaration).
     * 
     * Can be used to generate multiple classes in the same unit.
     * 
     * @param name
     * @param sb
     * @throws ParseException
     */
    public void generateCode(String name, StringBuilder sb, Function<String, ParameterType> inputParameterTypes)
            throws ParseException {
        this.parameterTypes = inputParameterTypes;
        this.name = name;
        StringBuilder body = new StringBuilder();
        parse(body);

        sb.append("\n");
        sb.append("public class ").append(name).append(" implements OLEvaluator {\n");

        sb.append("\n");

        // janino doesn't support generics
        // sb.append(" public Object evaluate(GlobalVariables globalVariables, List<RawEngValue> inputList) {\n");
        sb.append("    public Object evaluate(GlobalVariables globalVariables, List inputList) {\n");
        if ("DERIVED".equals(name)) {
            sb.append("    System.out.println(inputList);\n");
        }
        for (int i = 0; i < inputParams.size(); i++) {
            String paraName = inputParams.get(i);
            sb.append("        RawEngValue ")
                    .append(paraName)
                    .append(" = (RawEngValue) inputList.get(" + i + ");\n");
        }

        for (Variable v : localVariables.values()) {
            sb.append("        ").append(v.type.javaType()).append(" ").append(v.name);
            if (defaultValuesForLocalVariables) {
                sb.append("=0");
            }
            sb.append(";\n");
        }
        sb.append(body);
        sb.append("    }\n}");
    }

    protected String getReturnCode(ExpressionCode ec) {
        ParameterType outType = parameterTypes.apply(name);
        if (outType == null) {
            return ec.code;
        }
        DataEncoding encoding = ((BaseDataType) outType).getEncoding();

        if ((encoding instanceof NumericDataEncoding) && ec.type == Type.BOOLEAN) {
            return "OLFunction.bool2int(" + ec.code + ")";
        } else if ((encoding instanceof IntegerDataEncoding) && (ec.type == Type.DOUBLE)) {
            return "(long) (" + ec.code + ")";
        } else {
            return ec.code;
        }
    }

    protected void addVariable(String name, Type type) throws ParseException {
        Variable v = localVariables.get(name);
        if (v == null) {
            localVariables.put(name, new Variable(name, type));
        } else if (v.type != type) {
            if (v.type == Type.BOOLEAN) {
                throw new ParseException("Cannot convert " + type + " too boolean");
            } else if (v.type == Type.LONG && type == Type.DOUBLE) {
                v.type = Type.DOUBLE;
            }
        }
    }

    protected ExpressionCode getIdentifierCode(String id) throws ParseException {
        if (isLocalVar(id)) {
            Variable v = localVariables.get(id);
            if (v == null) {
                throw new ParseException("Uninitialized local variable '" + id + "'");
            }
            return new ExpressionCode(v.type, id);
        } else if (isGlobalVar(id)) {
            return new ExpressionCode(Type.DOUBLE, "globalVariables.get(\"" + id + "\")");
        } else { // parameter
            int idx = id.indexOf('.');
            String paraName;
            ParameterView paraView = null;
            if (idx > 0) {
                paraName = id.substring(0, idx);
                paraView = ParameterView.parse(id.substring(idx + 1));
            } else {
                paraName = id;
            }
            if (paraName.startsWith("$")) {
                paraName = paraName.substring(1);
                noTriggerParams.add(paraName);
            }
            ParameterType ptype = parameterTypes.apply(paraName);
            if (ptype == null) {
                throw new ParseException("Unknonw parameter '" + paraName + "'");
            }
            addInputParam(paraName);

            if(paraView == null) {
                if(ptype instanceof EnumeratedParameterType) {
                    return new ExpressionCode(Type.ENUM, paraName);
                } else {
                    paraView = ParameterView.ENG;
                }
            }

            if (paraView == ParameterView.ENG) {
                String t = getEngType(ptype).name();
                String v = paraName + ".getEngValue().get" + t.substring(0, 1) + t.substring(1).toLowerCase()
                        + "Value()";
                if (ptype instanceof IntegerParameterType) {
                    return new ExpressionCode(Type.LONG, v);
                } else if (ptype instanceof FloatParameterType) {
                    return new ExpressionCode(Type.DOUBLE, v);
                } else if (ptype instanceof BooleanParameterType) {
                    return new ExpressionCode(Type.BOOLEAN, v);
                } else if (ptype instanceof EnumeratedParameterType) {
                    return new ExpressionCode(Type.STRING, v);
                } else {
                    throw new ParseException("Unsupported parameter of type " + ptype);
                }
            } else if (paraView == ParameterView.RAW) {
                DataEncoding encoding = ((BaseDataType) ptype).getEncoding();
                String t = DataEncodingDecoder.getRawType(encoding).name();
                String v = paraName + ".getRawValue().get" + t.substring(0, 1) + t.substring(1).toLowerCase()
                        + "Value()";
                if (encoding instanceof IntegerDataEncoding) {
                    return new ExpressionCode(Type.LONG, v);
                } else if (encoding instanceof FloatDataEncoding) {
                    return new ExpressionCode(Type.DOUBLE, v);
                } else if (encoding instanceof BooleanDataEncoding) {
                    return new ExpressionCode(Type.BOOLEAN, v);
                } else {
                    throw new ParseException("Unsupported parameter of type " + ptype);
                }
            } else if (paraView == ParameterView.TIME) {
                return new ExpressionCode(Type.DOUBLE, "OLFunction.getObTime(" + paraName + ")");
            } else {
                throw new IllegalStateException("Unknown parameter view " + paraView);
            }
        }
    }

    protected boolean isGlobalVar(String id) {
        return id.startsWith("GVAR");
    }

    protected boolean isLocalVar(String id) {
        return id.startsWith("VAR");
    }

    protected ExpressionCode getSynthExpression(List<String> paraList) {
        boolean first = true;
        StringBuilder sb = new StringBuilder();
        sb.append("OLFunction.synth( new RawEngValue[] {");
        for (String paraName : paraList) {
            addInputParam(paraName);
            if (first) {
                first = false;
            } else {
                sb.append(", ");
            }
            sb.append(paraName);
        }
        sb.append("})");
        return new ExpressionCode(Type.BOOLEAN, sb.toString());
    }

    private void addInputParam(String paraName) {
        if (!inputParams.contains(paraName)) {
            inputParams.add(paraName);
        }
    }

    protected abstract void parse(StringBuilder body) throws ParseException;

    public boolean isNoTrigger(String pname) {
        return noTriggerParams.contains(pname);
    }

    public List<String> getInputParameters() {
        return inputParams;
    }

    /**
     * Parses the format ddd.hh.mm.ss[mmm] into milliseconds
     * 
     */
    public static long parseDeltaTime(String timeString) throws ParseException {
        String regex = "(\\d{3})\\.(\\d{2})\\.(\\d{2})\\.(\\d{2})(:?.(\\d{3}))?";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(timeString);

        if (!matcher.matches()) {
            throw new ParseException("Cannot parse delta time '" + timeString + "'");
        }

        int days = Integer.parseInt(matcher.group(1));
        int hours = Integer.parseInt(matcher.group(2));
        int minutes = Integer.parseInt(matcher.group(3));
        int seconds = Integer.parseInt(matcher.group(4));
        
        int millis = matcher.group(5) == null ? 0 : Integer.parseInt(matcher.group(5));

        long r = millis;
        r += seconds * 1000L;
        r += minutes * 60 * 1000L;
        r += hours * 60 * 60 * 1000L;
        r += days * 24 * 60 * 60 * 1000L;

        return r;
    }

    /**
     * Parses the ADS format: yyyy.ddd.hh.mm.ss[mmm] into milliseconds
     * 
     */
    public static long parseAdsTime(String timeString) throws ParseException {
        String regex = "(\\d{4})\\.(\\d{1,3})\\.(\\d{1,2})\\.(\\d{1,2})\\.(\\d{1,2})\\[(\\d{1,3})\\]";
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(timeString);

        if (!matcher.matches()) {
            throw new IllegalArgumentException("Time string is not in the correct format.");
        }

        // Extract the different parts of the time
        int year = Integer.parseInt(matcher.group(1));
        int doy = Integer.parseInt(matcher.group(2));
        int hour = Integer.parseInt(matcher.group(3));
        int minute = Integer.parseInt(matcher.group(4));
        int second = Integer.parseInt(matcher.group(5));
        int millisec = Integer.parseInt(matcher.group(6));
        TaiUtcConverter.DateTimeComponents dtc = new TaiUtcConverter.DateTimeComponents(year, doy, hour, minute, second,
                millisec);
        return TimeEncoding.fromUtc(dtc);
    }

    /**
     * Parses the format yyyy-mm-ddThh:mm:ss.[uuuuuuu] into milliseconds
     * 
     */
    public static long parseAsciiATime(String timeString) throws ParseException {
        try {
            return TimeEncoding.parse(timeString);
        } catch (IllegalArgumentException e) {
            throw new ParseException(e.getMessage());
        }
    }

    /**
     * Parses the format yyyy-dddThh:mm:ss.[uuuuuu] into milliseconds
     * 
     */
    public static long parseAsciiBTime(String timeString) throws ParseException {
        try {
            return TimeEncoding.parse(timeString);
        } catch (IllegalArgumentException e) {
            throw new ParseException(e.getMessage());
        }
    }

    public static org.yamcs.protobuf.Yamcs.Value.Type getEngType(ParameterType ptype) {
        if (ptype instanceof IntegerParameterType) {
            IntegerParameterType ipt = (IntegerParameterType) ptype;
            if (ipt.getSizeInBits() <= 32) {
                if (ipt.isSigned()) {
                    return org.yamcs.protobuf.Yamcs.Value.Type.SINT32;
                } else {
                    return org.yamcs.protobuf.Yamcs.Value.Type.UINT32;
                }
            } else {
                if (ipt.isSigned()) {
                    return org.yamcs.protobuf.Yamcs.Value.Type.SINT64;
                } else {
                    return org.yamcs.protobuf.Yamcs.Value.Type.UINT64;
                }
            }
        } else if (ptype instanceof FloatParameterType) {
            FloatParameterType fpt = (FloatParameterType) ptype;
            if (fpt.getSizeInBits() <= 32) {
                return org.yamcs.protobuf.Yamcs.Value.Type.FLOAT;
            } else {
                return org.yamcs.protobuf.Yamcs.Value.Type.DOUBLE;
            }
        } else if (ptype instanceof BooleanParameterType) {
            return org.yamcs.protobuf.Yamcs.Value.Type.BOOLEAN;
        } else if (ptype instanceof BinaryParameterType) {
            return org.yamcs.protobuf.Yamcs.Value.Type.BINARY;
        } else if (ptype instanceof StringParameterType) {
            return org.yamcs.protobuf.Yamcs.Value.Type.STRING;
        } else if (ptype instanceof EnumeratedParameterType) {
            return org.yamcs.protobuf.Yamcs.Value.Type.STRING;
        } else if (ptype instanceof AbsoluteTimeParameterType) {
            return org.yamcs.protobuf.Yamcs.Value.Type.TIMESTAMP;
        } else {
            throw new IllegalStateException("Unknonw parameter type '" + ptype + "'");
        }
    }

    static class Variable {
        String name;
        Type type;

        Variable(String name, Type type) {
            this.name = name;
            this.type = type;
        }

        public String toString() {
            return name + ":" + type;
        }
    }

    enum ParameterView {
        RAW, ENG, TIME;

        static ParameterView parse(String s) throws ParseException {
            if ("eng".equalsIgnoreCase(s)) {
                return ENG;
            } else if ("raw".equalsIgnoreCase(s)) {
                return RAW;
            } else if ("time".equalsIgnoreCase(s)) {
                return TIME;
            } else {
                throw new ParseException("Unknown parameter view '" + s + "'");
            }
        }
    }
}
