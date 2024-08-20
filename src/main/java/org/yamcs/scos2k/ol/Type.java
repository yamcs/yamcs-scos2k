package org.yamcs.scos2k.ol;

enum Type {
    DOUBLE, LONG, BOOLEAN, STRING, ENUM;

    public String javaType() {
        return this.name().toLowerCase();

    }
};
