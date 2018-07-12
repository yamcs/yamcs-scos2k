package org.yamcs.scos2k.ol;

import java.util.HashMap;
import java.util.Map;

public class Operation {
    static Map<Integer, Type> MATH_OP = new HashMap<>();
    static Map<Integer, Type> SHIFT_OP = new HashMap<>();
    static Map<Integer, Type> COMP = new HashMap<>();
    static {
        MATH_OP.put(key(Type.DOUBLE, Type.DOUBLE), Type.DOUBLE);
        MATH_OP.put(key(Type.DOUBLE, Type.BOOLEAN), Type.DOUBLE);
        MATH_OP.put(key(Type.DOUBLE, Type.LONG), Type.DOUBLE);
        MATH_OP.put(key(Type.LONG, Type.LONG), Type.LONG);

        SHIFT_OP.put(key(Type.LONG, Type.LONG), Type.LONG);

        COMP.put(key(Type.DOUBLE, Type.LONG), Type.BOOLEAN);
        COMP.put(key(Type.DOUBLE, Type.DOUBLE), Type.BOOLEAN);
        COMP.put(key(Type.LONG, Type.LONG), Type.BOOLEAN);
        COMP.put(key(Type.BOOLEAN, Type.BOOLEAN), Type.BOOLEAN);
    }

    final Map<Integer, Type> typeOps;
    final String code;

    final static Operation PLUS = new MathOperation("+");
    final static Operation MINUS = new MathOperation("-");
    final static Operation STAR = new MathOperation("*");
    final static Operation SLASH = new MathOperation("/");
    final static Operation MODULUS = new MathOperation("%");
    final static Operation SMALLER_THAN = new Operation("<", COMP);
    final static Operation BIGGER_THAN = new Operation(">", COMP);
    final static Operation SMALLER_THAN_EQ = new Operation("<=", COMP);
    final static Operation BIGGER_THAN_EQ = new Operation(">=", COMP);
    final static Operation EQUAL = new Operation("==", COMP);
    final static Operation DIFFER = new Operation("<>", COMP);
    final static Operation SHIFT_RIGHT = new Operation(">>", SHIFT_OP);
    final static Operation SHIFT_LEFT = new Operation("<<", SHIFT_OP);
    final static Operation BITW_AND = new Operation("&", SHIFT_OP);
    final static Operation BITW_OR = new Operation("|", SHIFT_OP);
    final static Operation BITW_NOT = new Operation("~", SHIFT_OP);

    // nand
    // nor
    final static Operation BITW_XOR = new Operation("^", SHIFT_OP);
    final static Operation LOGIC_AND = new Operation("&&", COMP);
    final static Operation LOGIC_OR = new Operation("||", COMP);
    final static Operation LOGIC_NOT = new Operation("!", COMP);

    final static Operation POW = new Operation("Math.pow", COMP) {
        @Override
        public ExpressionCode getCode(ExpressionCode ec1, ExpressionCode ec2) {
            return new ExpressionCode(Type.DOUBLE, "Math.pow(" + ec1.code + ", " + ec2.code + ")");
        }
    };

    Operation(String code, Map<Integer, Type> typeOps) {
        this.code = code;
        this.typeOps = typeOps;
    }

    public String getOpCode() {
        return code;
    }

    public ExpressionCode getCode(ExpressionCode ec1, ExpressionCode ec2) throws ParseException {
        Type rt = getTypeResult(ec1.type, ec2.type);
        if (rt == null) {
            throw new ParseException("Unsupported operation " + code + " for expressions: " + ec1 + " and " + ec2);
        }
        return new ExpressionCode(rt, ec1.code + " " + code + " " + ec2.code);
    }

    protected Type getTypeResult(Type type1, Type type2) throws ParseException {
        Type rt = typeOps.get(key(type1, type2));
        if (rt == null) {
            rt = typeOps.get(key(type2, type1));
        }

        return rt;
    }

    static int key(Type t1, Type t2) {
        return t1.ordinal() << 16 | t2.ordinal();
    }

    public String toString() {
        return code;
    }

    static class MathOperation extends Operation {
        MathOperation(String code) {
            super(code, Operation.MATH_OP);
        }

        @Override
        public ExpressionCode getCode(ExpressionCode ec1, ExpressionCode ec2) throws ParseException {
            //promote boolean to long
            Type type1 = ec1.type;
            String code1 = ec1.code;
            if (type1 == Type.BOOLEAN) {
                code1 = "OLFunction.bool2int(" + ec1.code + ")";
                type1 = Type.LONG;
            }
            Type type2 = ec2.type;
            String code2 = ec2.code;
            if (type2 == Type.BOOLEAN) {
                code2 = "OLFunction.bool2int(" + ec2.code + ")";
                type2 = Type.LONG;
            }

            Type rt = getTypeResult(type1, type2);

            if (rt == null) {
                throw new ParseException("Unsupported operation " + code + " for expressions: " + ec1 + " and " + ec2);
            }
            return new ExpressionCode(rt, code1 + " " + code + " " + code2);
        }
    }
}
