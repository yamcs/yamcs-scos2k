package org.yamcs.scos2k.ol;

public class ExpressionCode {
   

    Type type;
    String code;
    ExpressionCode(Type type, String code) {
        this.type = type;
        this.code = code;
    }
    
    static ExpressionCode getCode(ExpressionCode ec1, ExpressionCode ec2, Operation op) throws ParseException {
        if(ec2==null) {
            return ec1;
        }
        return op.getCode(ec1, ec2);
    }
    
    @Override
    public String toString() {
        return "ExpressionCode [type=" + type + ", code=" + code + "]";
    }
}
