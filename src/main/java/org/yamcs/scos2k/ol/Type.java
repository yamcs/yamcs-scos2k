package org.yamcs.scos2k.ol;

enum Type {
    DOUBLE , LONG,  BOOLEAN;
    public String javaType() {
        return this.name().toLowerCase();
    }
 };