package org.yamcs.scos2k.ol;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

import org.yamcs.xtce.BaseDataType;
import org.yamcs.xtce.BooleanDataEncoding;
import org.yamcs.xtce.BooleanParameterType;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.FloatParameterType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.NumericDataEncoding;
import org.yamcs.xtce.NumericParameterType;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtceproc.DataEncodingDecoder;
import org.yamcs.xtceproc.ParameterTypeProcessor;
import org.yamcs.xtceproc.ParameterTypeUtils;

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
    
    /**
     * Generates the import statement required before the class definition
     * 
     * @param code
     */
    public static void generateCodeImports(StringBuilder code) {
        code.append("package org.yamcs.scos2k.ol.generated;\n");
        code.append("import java.util.List;\n");
        code.append("import org.yamcs.parameter.ParameterValue;\n");
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
     *            - function that gives the types for the input parameters;
     *            the function should return null if the parameter is unknown (a ParseException will be thrown in this
     *            case).
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
    public void generateCode(String name, StringBuilder sb, Function<String, ParameterType> inputParameterTypes) throws ParseException {
        this.parameterTypes = inputParameterTypes;
        this.name = name;
        StringBuilder body = new StringBuilder();
        parse(body);

        sb.append("\n");
        sb.append("public class ").append(name).append(" implements OLEvaluator {\n");
        
        sb.append("\n");
        // sb.append("    public Object evaluate(List<ParameterValue> inputList) {\n"); // janino doesn't support templates
        sb.append("    public Object evaluate(GlobalVariables globalVariables, List inputList) {\n");
        if("DERIVED".equals(name)) {
            sb.append("    System.out.println(inputList);\n");
        }
        for (int i =0; i<inputParams.size(); i++) {
            String paraName = inputParams.get(i);
            sb.append("        ParameterValue ").append(paraName).append(" = (ParameterValue) inputList.get("+i+");\n");
        }
        
        for (Variable v : localVariables.values()) {
            sb.append("        ").append(v.type.javaType()).append(" ").append(v.name).append(";\n");
        }
        sb.append(body);
        sb.append("    }\n}");
    }

   

    protected String getReturnCode(ExpressionCode ec) {
        ParameterType otype = parameterTypes.apply(name);
        DataEncoding encoding = ((BaseDataType)otype).getEncoding();
        
        if ((encoding instanceof NumericDataEncoding) && ec.type == Type.BOOLEAN) {
            return "OLFunction.bool2int("+ec.code+")";
        } else if((encoding instanceof IntegerDataEncoding) && (ec.type == Type.DOUBLE)) {
            return "(long) ("+ec.code+")";
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
            String paraProp;
            if (idx > 0) {
                paraName = id.substring(0, idx);
                paraProp = id.substring(idx + 1);
            } else {
                paraName = id;
                paraProp = "eng";
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
            if ("eng".equals(paraProp)) {
                String t = ParameterTypeUtils.getEngType(ptype).name();
                String v = paraName + ".getEngValue().get" + t.substring(0, 1) + t.substring(1).toLowerCase()
                        + "Value()";
                if (ptype instanceof IntegerParameterType) {
                    return new ExpressionCode(Type.LONG, v);
                } else if (ptype instanceof FloatParameterType) {
                    return new ExpressionCode(Type.DOUBLE, v);
                } else if (ptype instanceof BooleanParameterType) {
                    return new ExpressionCode(Type.BOOLEAN, v);
                } else {
                    throw new ParseException("Unsupported parameter of type " + ptype);
                }
            } else if ("raw".equals(paraProp)) {
                DataEncoding encoding = ((BaseDataType)ptype).getEncoding();
                System.out.println("pname: " + paraName+" encoding: "+encoding);
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
            } else if ("time".equals(paraProp)) {
                return new ExpressionCode(Type.DOUBLE, "OLFunction.getObTime("+paraName + ")");
            } else {
                throw new ParseException("Unknown property '" + paraProp + "' for parameter '" + paraName + "'");
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
        sb.append("OLFunction.synth( new ParameterValue[] {");
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
    
    
    
}
