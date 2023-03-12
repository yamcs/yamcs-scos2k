package org.yamcs.scos2k;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.yamcs.xtce.Argument;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.mdb.DatabaseLoadException;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.MetaCommand;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.SplinePoint;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.BinaryDataEncoding.Type;
import org.yamcs.xtce.IntegerDataEncoding.Encoding;
import org.yamcs.xtce.SequenceEntry.ReferenceLocationType;
import org.yamcs.xtce.StringDataEncoding.SizeType;

public class MibLoaderBits {
    static Map<Integer, IntegerParameterType> integerPtype = new HashMap<>();
    static final byte[] PTC9_10_SIZE_IN_BITS = new byte[] { -1, 48, 64, 8, 16, 24, 32, 16, 24, 32, 40, 24, 32, 40, 48,
            32, 40, 48, 56 };
    static final String PARA_NAME_APID = "ccsds-apid";
    static final String PARA_NAME_PUS_TYPE = "pus-packet_type";
    static final String PARA_NAME_PUS_STYPE = "pus-packet_stype";
    static final String CONTAINER_NAME_ROOT = "ccsds-default";

    static class MibLoaderContext {
        String filename;
        int lineNum;

        MibLoaderContext(String filename, int lineNum) {
            this.filename = filename;
            this.lineNum = lineNum;
        }

        public MibLoaderContext clone() {
            return new MibLoaderContext(filename, lineNum);
        }

    }

    static class MibLoadException extends DatabaseLoadException {
        private static final long serialVersionUID = 1L;
        final MibLoaderContext ctx;

        public MibLoadException(String msg) {
            super(msg);
            this.ctx = null;
        }

        public MibLoadException(MibLoaderContext ctx, String msg) {
            super(msg);
            this.ctx = ctx;
        }

        public String toString() {
            if (ctx != null) {
                return ctx.filename + ":" + ctx.lineNum + ": " + getMessage();
            } else {
                return getMessage();
            }
        }
    }

    static class ParameterTypeCache {
        Map<String, ParameterType> cache = new HashMap<>();

        ParameterType get(MibParameter mp) {
            return cache.get(key(mp));
        }

        public void add(MibParameter mp, ParameterType ptype) {
            cache.put(key(mp), ptype);

        }

        private String key(MibParameter mp) {
            return String.format("%d|%d|%s|%s|%s|%d|%s|%s", mp.ptc, mp.pfc, mp.pcfCateg, mp.pcfCurtx, mp.pcfInter,
                    mp.vplb,
                    mp.unit, mp.pcfNatur);
        }
    }

    /**
     * 
     * @param ctx
     * @param ptc
     *            - parameter type code
     * @param pfc
     *            - parameter format code
     * @param vplb
     *            size in bytes of the size tag
     * @return
     */
    static DataEncoding.Builder<?> getDataEncoding(MibLoaderContext ctx, int ptc, int pfc, int vplb) {
        if (ptc == 1) {
            if (pfc == 0) {
                return new IntegerDataEncoding.Builder().setSizeInBits(1);
            }
        } else if (ptc == 2) {
            if (pfc > 1 || pfc < 33) {
                return new IntegerDataEncoding.Builder().setSizeInBits(pfc).setEncoding(Encoding.UNSIGNED);
            }
        } else if (ptc == 3 || ptc == 4) {

            int sizeInBits;
            if (pfc < 13) {
                sizeInBits = pfc + 4;
            } else if (pfc == 13) {
                sizeInBits = 24;
            } else if (pfc == 14) {
                sizeInBits = 32;
            } else if (pfc == 15) {
                sizeInBits = 48;
            } else if (pfc == 16) {
                sizeInBits = 64;
            } else {
                throw new MibLoadException(ctx,
                        "Unknown parameter type (" + ptc + "," + pfc + ")");
            }
            IntegerDataEncoding.Builder encoding = new IntegerDataEncoding.Builder().setSizeInBits(sizeInBits);
            if (ptc == 3) {
                encoding.setEncoding(Encoding.UNSIGNED);
            } else {
                encoding.setEncoding(Encoding.TWOS_COMPLEMENT);
            }
            return encoding;
        } else if (ptc == 5) {
            if (pfc == 1) {
                return new FloatDataEncoding.Builder().setSizeInBits(32)
                        .setFloatEncoding(org.yamcs.xtce.FloatDataEncoding.Encoding.IEEE754_1985);
            } else if (pfc == 2) {
                return new FloatDataEncoding.Builder().setSizeInBits(64)
                        .setFloatEncoding(org.yamcs.xtce.FloatDataEncoding.Encoding.IEEE754_1985);
            } else if (pfc == 3) {
                return new FloatDataEncoding.Builder().setSizeInBits(32)
                        .setFloatEncoding(org.yamcs.xtce.FloatDataEncoding.Encoding.MILSTD_1750A);
            } else if (pfc == 4) {
                return new FloatDataEncoding.Builder().setSizeInBits(48)
                        .setFloatEncoding(org.yamcs.xtce.FloatDataEncoding.Encoding.MILSTD_1750A);
            } else {
                throw new MibLoadException(ctx, "Unknown parameter type (" + ptc + "," + pfc + ")");
            }
        } else if (ptc == 6) {
            if (pfc < 1 || pfc > 64) {
                throw new MibLoadException(ctx, "Unknown parameter type (ptc,pfc): (" + ptc + "," + pfc + ")");
            }
            // BinaryDataEncoding encoding = new BinaryDataEncoding(Type.FIXED_SIZE);
            // encoding.setSizeInBits(pfc);
            IntegerDataEncoding.Builder encoding = new IntegerDataEncoding.Builder().setSizeInBits(pfc);
            encoding.setEncoding(Encoding.UNSIGNED);
            return encoding;
        } else if (ptc == 7) {
            if (pfc < 0) {
                throw new MibLoadException(ctx, "Unknown parameter type (ptc,pfc): (" + ptc + "," + pfc + ")");
            }
            if (pfc == 0) {
                if (vplb < 0) {
                    throw new MibLoadException(ctx, "Invalid size of the size tag (vplb) '" + vplb + "'");
                }
                BinaryDataEncoding.Builder encoding = new BinaryDataEncoding.Builder().setType(Type.LEADING_SIZE);
                encoding.setSizeInBitsOfSizeTag(8 * vplb);
                return encoding;
            } else {
                BinaryDataEncoding.Builder encoding = new BinaryDataEncoding.Builder().setType(Type.FIXED_SIZE);
                encoding.setSizeInBits(pfc * 8);
                return encoding;
            }
        } else if (ptc == 8) {
            if (pfc < 0) {
                throw new MibLoadException(ctx, "Unknown parameter type (ptc,pfc): (" + ptc + "," + pfc + ")");
            }
            if (pfc == 0) {
                if (vplb < 0) {
                    throw new MibLoadException(ctx, "Invalid size of the size tag (vplb) '" + vplb + "'");
                }
                StringDataEncoding.Builder encoding = new StringDataEncoding.Builder()
                        .setSizeType(SizeType.LEADING_SIZE)
                        .setSizeInBitsOfSizeTag(8 * vplb);
                return encoding;
            } else {
                StringDataEncoding.Builder encoding = new StringDataEncoding.Builder()
                        .setSizeType(SizeType.FIXED)
                        .setSizeInBits(pfc * 8);
                return encoding;
            }
        } else if (ptc == 9) {
            if (pfc < 0 || (pfc > 18 && pfc != 30)) {
                throw new MibLoadException(ctx, "Unknown parameter type (ptc,pfc): (" + ptc + "," + pfc + ")");
            }
            BinaryDataEncoding.Builder encoding = new BinaryDataEncoding.Builder().setType(Type.CUSTOM);
            CustomAlgorithm customAlgo = new CustomAlgorithm("absolute_cuc_" + ptc + "_" + pfc);
            customAlgo.setLanguage("java");
            customAlgo.setAlgorithmText("org.yamcs.scos2k.TimeDecoder({'ptc':" + ptc + ", 'pfc':" + pfc + "})");
            encoding.setFromBinaryTransformAlgorithm(customAlgo);
            int sizeInBits = (pfc == 30) ? 64 : PTC9_10_SIZE_IN_BITS[pfc];
            encoding.setSizeInBits(sizeInBits);
            return encoding;
        } else if (ptc == 10) {
            if (pfc < 3 || pfc > 18) {
                throw new MibLoadException(ctx, "Unknown parameter type (ptc,pfc): (" + ptc + "," + pfc + ")");
            }
            BinaryDataEncoding.Builder encoding = new BinaryDataEncoding.Builder().setType(Type.CUSTOM);
            CustomAlgorithm customAlgo = new CustomAlgorithm("relative_cuc_" + ptc + "_" + pfc);
            customAlgo.setLanguage("java");
            customAlgo.setAlgorithmText("bububu relative cuc " + ptc + ", " + pfc);
            encoding.setFromBinaryTransformAlgorithm(customAlgo);
            encoding.setSizeInBits((int) PTC9_10_SIZE_IN_BITS[pfc]);
            return encoding;
        } else if (ptc == 11) {// deduced parameter
            return null;
        }
        throw new MibLoadException(ctx, "Unknown parameter type (ptc,pfc): (" + ptc + "," + pfc + ")");
    }

    static class PidRecord {
        int type;
        int stype;
        int apid;
        long pi1;
        long pi2;
        long spid;
        String descr;
        int dfhsize;

        public int getApidTypeSubtype() {
            return (apid << 16) | (type << 8) | stype;
        }
    }

    static class PicRecord {
        int type;
        int stype;
        int apid;
        int pi1Offset;
        int pi1Width;
        int pi2Offset;
        int pi2Width;

        public int getTypeSubtype() {
            return (type << 8) | stype;
        }
    }

    static class TpcfRecord {
        String name;
        int size;
    }

    static class DeducedParameter {
        String pname;
        String refPname;

        public DeducedParameter(String pname, String refPname) {
            this.pname = pname;
            this.refPname = refPname;
        }
    }

    static class PrfRecord {
        List<String> mins = new ArrayList<>();
        List<String> maxs = new ArrayList<>();

        String descr;
        String inter;
        String dspfmt;
        String radix;

        public void addMinMax(String min, String max) {
            mins.add(min);
            maxs.add(max);
        }
    }

    static class OcpRecord {
        @Override
        public String toString() {
            return "OcpRecord [pos=" + pos + ", type=" + type + ", lvalu=" + lvalu + ", hvalu=" + hvalu + ", rlchk="
                    + rlchk + ", valpar=" + valpar + "]";
        }

        int pos;
        String type;
        String lvalu;
        String hvalu;
        String rlchk;
        long valpar;
    }

    static class CafRecord {
        @Override
        public String toString() {
            return "CafRecord [engfmt=" + engfmt + ", rawfmt=" + rawfmt + ", radix=" + radix + "]";
        }

        String engfmt;
        String rawfmt;
        String radix;
    }

    static class CcaRecord {
        String engfmt;
        String rawfmt;
        String radix;
        List<SplinePoint> splines = new ArrayList<>();
    }

    static class CurRecord {
        @Override
        public String toString() {
            return "CurRecord [pos=" + pos + ", rlchk=" + rlchk + ", valpar=" + valpar + ", select=" + select + "]";
        }

        int pos;
        String rlchk;
        long valpar;
        String select;
    }

    static class VpdRecord {

        int pos;
        String name;
        int grpSize;
        int fixRep;
        String choice;
        int offset;

        @Override
        public String toString() {
            return "VpdRecord [pos=" + pos + ", name=" + name + ", grpSize=" + grpSize + ", fixRep=" + fixRep
                    + ", choice=" + choice + ", offset=" + offset + "]";
        }
    }

    static class CpcRecord {
        String pname;
        String descr;
        int ptc;
        int pfc;
        String unit;
        String categ;
        String prfref;
        String ccaref;
        String pafref;
        String inter;
        String defval;
        String corr;
    }

    static class CdfRecord {
        String eltype;
        String descr;
        int ellen;
        int position;
        int grpsize;
        CpcRecord cpc;
        String inter;
        String value;
        String tmid;
        int vplb = -1;
    }

    static class TcHeaderRecord {
        String name;
        MetaCommand mc;
        Argument apid;
        Argument type;
        Argument subType;
        Argument ack;
    }

    /**
     * Creates the root CCSDS container containing the APID, TYPE and SUBTYPE and a list of sub-containers one for each
     * type
     * 
     * @param spaceSystem
     * @param typeOffset
     * @param subtypeOffset
     * @param types
     */
    static void createRootPusContainers(SpaceSystem spaceSystem, int typeOffset, int subtypeOffset) {
        SequenceContainer ccsds = new SequenceContainer(CONTAINER_NAME_ROOT);
        spaceSystem.addSequenceContainer(ccsds);
        // spaceSystem.setRootSequenceContainer(ccsds);
        Parameter ccsdsApid = new Parameter(PARA_NAME_APID);
        IntegerParameterType ccsdsApidType = getIntegerParameterType(11);
        ccsdsApid.setParameterType(ccsdsApidType);
        ccsds.addEntry(new ParameterEntry(5, ReferenceLocationType.CONTAINER_START, ccsdsApid));
        spaceSystem.addParameter(ccsdsApid);

        IntegerParameterType uint8 = getIntegerParameterType(8);

        Parameter pusPacketType = new Parameter(PARA_NAME_PUS_TYPE);
        pusPacketType.setParameterType(uint8);
        ccsds.addEntry(new ParameterEntry(8 * typeOffset, ReferenceLocationType.CONTAINER_START, pusPacketType));
        spaceSystem.addParameter(pusPacketType);

        Parameter pusPacketSubType = new Parameter(PARA_NAME_PUS_STYPE);
        pusPacketSubType.setParameterType(uint8);
        ccsds.addEntry(new ParameterEntry(8 * subtypeOffset, ReferenceLocationType.CONTAINER_START, pusPacketSubType));
        spaceSystem.addParameter(pusPacketSubType);
    }

    static SequenceContainer createSubcontainer(SpaceSystem spaceSystem, int apid, int type, int stype, String name) {
        Parameter ccsdsApid = spaceSystem.getParameter(PARA_NAME_APID);
        Parameter pusPacketType = spaceSystem.getParameter(PARA_NAME_PUS_TYPE);
        Parameter pusPacketSubType = spaceSystem.getParameter(PARA_NAME_PUS_STYPE);
        SequenceContainer ccsds = spaceSystem.getSequenceContainer(CONTAINER_NAME_ROOT);

        SequenceContainer container = new SequenceContainer(name);
        container.setBaseContainer(ccsds);
        ComparisonList cl = new ComparisonList();
        cl.addComparison(
                new Comparison(new ParameterInstanceRef(ccsdsApid, false), Integer.toString(apid),
                        OperatorType.EQUALITY));
        cl.addComparison(
                new Comparison(new ParameterInstanceRef(pusPacketType, false), Integer.toString(type),
                        OperatorType.EQUALITY));
        cl.addComparison(
                new Comparison(new ParameterInstanceRef(pusPacketSubType, false), Integer.toString(stype),
                        OperatorType.EQUALITY));
        container.setRestrictionCriteria(cl);
        spaceSystem.addSequenceContainer(container);
        return container;
    }

    static String getContainerName(int apid, int type, int stype) {
        return String.format("pus_%d_%02x%02x", apid, type, stype);
    }

    static String sanitizeName(String name) {
        return name.replaceAll("[\\s/]", "_");
    }

    static IntegerParameterType getIntegerParameterType(int bitsize) {
        if ((bitsize < 1) || bitsize > 64) {
            throw new IllegalArgumentException("bitsize " + bitsize + " not allowed");
        }
        return integerPtype.computeIfAbsent(bitsize, k -> {
            IntegerParameterType ptype = new IntegerParameterType.Builder()
                    .setName("mib-uint" + bitsize)
                    .setEncoding(new IntegerDataEncoding.Builder().setSizeInBits(bitsize))
                    .setSigned(false)
                    .build();
            return ptype;
        });
    }
}
