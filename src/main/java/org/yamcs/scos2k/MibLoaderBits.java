package org.yamcs.scos2k;

import java.util.HashMap;
import java.util.Map;

import org.yamcs.xtce.AggregateArgumentType;
import org.yamcs.xtce.AggregateDataType;
import org.yamcs.xtce.AlgorithmCalibrator;
import org.yamcs.xtce.BinaryDataEncoding;
import org.yamcs.xtce.Comparison;
import org.yamcs.xtce.ComparisonList;
import org.yamcs.xtce.CustomAlgorithm;
import org.yamcs.xtce.DataEncoding;
import org.yamcs.mdb.DatabaseLoadException;
import org.yamcs.xtce.FloatDataEncoding;
import org.yamcs.xtce.IntegerArgumentType;
import org.yamcs.xtce.IntegerDataEncoding;
import org.yamcs.xtce.IntegerParameterType;
import org.yamcs.xtce.Member;
import org.yamcs.xtce.OperatorType;
import org.yamcs.xtce.Parameter;
import org.yamcs.xtce.ParameterEntry;
import org.yamcs.xtce.ParameterInstanceRef;
import org.yamcs.xtce.ParameterType;
import org.yamcs.xtce.SequenceContainer;
import org.yamcs.xtce.SpaceSystem;
import org.yamcs.xtce.StringDataEncoding;
import org.yamcs.xtce.BinaryDataEncoding.Type;
import org.yamcs.xtce.IntegerDataEncoding.Encoding;
import org.yamcs.xtce.IntegerDataType;
import org.yamcs.xtce.SequenceEntry.ReferenceLocationType;
import org.yamcs.xtce.StringDataEncoding.SizeType;

public class MibLoaderBits {
    static Map<Integer, IntegerParameterType> integerParameterTypes = new HashMap<>();
    static Map<Integer, IntegerArgumentType> integerArgumentTypes = new HashMap<>();
    static final byte[] PTC10_SIZE_IN_BITS = new byte[] { -1, 48, 64, 8, 16, 24, 32, 16, 24, 32, 40, 24, 32, 40, 48,
            32, 40, 48, 56 };
    static final String PARA_NAME_APID = "ccsds_apid";
    static final String PARA_NAME_SEQCOUNT = "ccsds_seqcount";
    static final String PARA_NAME_PUS_TYPE = "pus_type";
    static final String PARA_NAME_PUS_STYPE = "pus_stype";
    static final String CONTAINER_NAME_ROOT = "ccsds-pus";

    // these parameters are added to the data part of PUS1 packets for being used in the command verification
    static final String PARA_NAME_PUS1_APID = "pus1_apid";
    static final String PARA_NAME_PUS1_SEQCOUNT = "pus1_seqcount";

    static class MibLoaderContext {
        String filename;
        int lineNum;
        final MibConfig conf;

        MibLoaderContext(MibConfig conf, String filename, int lineNum) {
            this.conf = conf;
            this.filename = filename;
            this.lineNum = lineNum;
        }

        public MibLoaderContext clone() {
            return new MibLoaderContext(conf, filename, lineNum);
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

        public MibLoadException(MibLoaderContext ctx, String msg, Throwable t) {
            super(msg, t);
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
            IntegerDataEncoding.Builder encoding = new IntegerDataEncoding.Builder();
            int numFineBytes;
            if (pfc == 0) {
                // pfield is encoded in the time, we should set an algorithm to decode it
                // TODO: implement the CucTimeDecoder algorithm
                /*
                encoding.setFromBinaryTransformAlgorithm(null);
                CustomAlgorithm decodingAlgo = new CustomAlgorithm("cuc_decoder" + ptc + "_" + pfc);
                decodingAlgo.setLanguage("java");
                decodingAlgo.setAlgorithmText("org.yamcs.algo.CucTimeDecoder()");
                encoding.setFromBinaryTransformAlgorithm(decodingAlgo);*/
                throw new MibLoadException(ctx, "Absolute time parameter with explicit pfield not implemented");
            } else if (pfc <= 18) {
                int numCoarseBytes = 1 + (pfc - 3) / 4;
                numFineBytes = (pfc - 3) % 4;
                encoding.setSizeInBits(8 * (numFineBytes + numCoarseBytes));
            } else if (pfc == 30) {
                throw new MibLoadException(ctx,
                        "Time encoding/decoding with pfc = 30 (Absolute Unix time format) not supported");
            } else {
                throw new MibLoadException(ctx, "Unknown parameter type (ptc,pfc): (" + ptc + "," + pfc + ")");
            }
            var tcoService = ctx.conf.tcoService;
            if (tcoService != null) {
                String tfb = "";

                if (ctx.conf.tcoFineBytes >= 0) {
                    if (numFineBytes < ctx.conf.tcoFineBytes) {
                        tfb = ", shiftBits: " + (ctx.conf.tcoFineBytes - numFineBytes) * 8;
                    } else if (numFineBytes > ctx.conf.tcoFineBytes) {
                        throw new MibLoadException("Parameter (ptc,pfc): (" + ptc + "," + pfc
                                + ") numFineBytes greater than configuration tcoFineBytes");
                    }
                }
                CustomAlgorithm tcoCalibrator = new CustomAlgorithm("tco_calibrator_" + ptc + "_" + pfc);
                tcoCalibrator.setLanguage("java");
                tcoCalibrator.setAlgorithmText(
                        "org.yamcs.algo.TcoCalibrator({tcoService: '" + tcoService + "'" + tfb + "})");
                encoding.setDefaultCalibrator(new AlgorithmCalibrator(tcoCalibrator));
            }
            return encoding;
        } else if (ptc == 10) {
            if (pfc < 3 || pfc > 18) {
                throw new MibLoadException(ctx, "Unknown parameter type (ptc,pfc): (" + ptc + "," + pfc + ")");
            }
            BinaryDataEncoding.Builder encoding = new BinaryDataEncoding.Builder().setType(Type.CUSTOM);
            CustomAlgorithm customAlgo = new CustomAlgorithm("relative_cuc_" + ptc + "_"
                    + pfc);
            customAlgo.setLanguage("java");
            customAlgo.setAlgorithmText("bububu relative cuc " + ptc + ", " + pfc);
            encoding.setFromBinaryTransformAlgorithm(customAlgo);
            encoding.setSizeInBits((int) PTC10_SIZE_IN_BITS[pfc]);
            return encoding;
        } else if (ptc == 11 || ptc == 12) {
            // PTC=11: deduced parameter
            // PTC=12: TM or TC packet
            return null;
        }
        throw new MibLoadException(ctx, "Unknown parameter type (ptc,pfc): (" + ptc + "," + pfc + ")");
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
        ccsdsApid.setParameterType(getUnsignedParameterType(spaceSystem, 11));
        ccsds.addEntry(new ParameterEntry(5, ReferenceLocationType.CONTAINER_START, ccsdsApid));
        spaceSystem.addParameter(ccsdsApid);

        Parameter ccsdsSeqCount = new Parameter(PARA_NAME_SEQCOUNT);
        ccsdsSeqCount.setParameterType(getUnsignedParameterType(spaceSystem, 16));
        ccsds.addEntry(new ParameterEntry(16, ReferenceLocationType.CONTAINER_START, ccsdsSeqCount));
        spaceSystem.addParameter(ccsdsSeqCount);

        IntegerParameterType uint8 = getUnsignedParameterType(spaceSystem, 8);

        Parameter pusPacketType = new Parameter(PARA_NAME_PUS_TYPE);
        pusPacketType.setParameterType(uint8);
        ccsds.addEntry(new ParameterEntry(8 * typeOffset, ReferenceLocationType.CONTAINER_START, pusPacketType));
        spaceSystem.addParameter(pusPacketType);

        Parameter pusPacketSubType = new Parameter(PARA_NAME_PUS_STYPE);
        pusPacketSubType.setParameterType(uint8);
        ccsds.addEntry(new ParameterEntry(8 * subtypeOffset, ReferenceLocationType.CONTAINER_START, pusPacketSubType));
        spaceSystem.addParameter(pusPacketSubType);
    }

    static void addParameterTypeToSpaceSystem(SpaceSystem spaceSystem, ParameterType ptype) {
        if (spaceSystem.getParameterType(ptype.getName()) == null) {
            spaceSystem.addParameterType(ptype);
        }
    }

    /**
     * Create a sub-container directly inheriting the ccsds one
     */
    static SequenceContainer createLevel1Subcontainer(SpaceSystem spaceSystem, int apid, int type, int stype,
            String name, int pus1DataOffset) {
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

        if (type == 1) {
            Parameter pus1Apid = spaceSystem.getParameter(PARA_NAME_PUS1_APID);
            if (pus1Apid == null) {
                pus1Apid = new Parameter(PARA_NAME_PUS1_APID);
                pus1Apid.setParameterType(getUnsignedParameterType(spaceSystem, 11));
                spaceSystem.addParameter(pus1Apid);
            }

            container.addEntry(
                    new ParameterEntry(8 * pus1DataOffset + 5, ReferenceLocationType.CONTAINER_START, pus1Apid));

            Parameter pus1SeqCount = spaceSystem.getParameter(PARA_NAME_PUS1_SEQCOUNT);
            if (pus1SeqCount == null) {
                pus1SeqCount = new Parameter(PARA_NAME_PUS1_SEQCOUNT);
                pus1SeqCount.setParameterType(getUnsignedParameterType(spaceSystem, 14));
                spaceSystem.addParameter(pus1SeqCount);
            }
            container.addEntry(
                    new ParameterEntry(8 * pus1DataOffset + 18, ReferenceLocationType.CONTAINER_START, pus1SeqCount));
        }

        spaceSystem.addSequenceContainer(container);
        return container;
    }

    static String getContainerName(int apid, int type, int stype) {
        return String.format("pus_%d_%02x%02x", apid, type, stype);
    }

    static String sanitizeName(String name) {
        return name.replaceAll("[\\s/]", "_");
    }

    static IntegerParameterType getUnsignedParameterType(SpaceSystem spaceSystem, int bitsize) {
        if ((bitsize < 1) || bitsize > 64) {
            throw new IllegalArgumentException("bitsize " + bitsize + " not allowed");
        }
        String name = "mib-ptype-uint" + bitsize;
        IntegerParameterType ptype = (IntegerParameterType) spaceSystem.getParameterType(name);
        if (ptype == null) {
            ptype = new IntegerParameterType.Builder()
                    .setName(name)
                    .setEncoding(new IntegerDataEncoding.Builder().setSizeInBits(bitsize))
                    .setSigned(false)
                    .build();
            spaceSystem.addParameterType(ptype);
        }

        return ptype;
    }

    static IntegerArgumentType getIntegerArgumentType(int bitsize) {
        if ((bitsize < 1) || bitsize > 64) {
            throw new IllegalArgumentException("bitsize " + bitsize + " not allowed");
        }
        return integerArgumentTypes.computeIfAbsent(bitsize, k -> {
            IntegerArgumentType atype = new IntegerArgumentType.Builder()
                    .setName("mib-ptype-uint" + bitsize)
                    .setEncoding(new IntegerDataEncoding.Builder().setSizeInBits(bitsize))
                    .setSigned(false)
                    .build();
            return atype;
        });
    }

    static IntegerDataType getIntegerDataType(SpaceSystem spaceSystem, int bitsize, boolean argument) {
        if (argument) {
            return getIntegerArgumentType(bitsize);
        } else {
            return getUnsignedParameterType(spaceSystem, bitsize);
        }
    }

    static void addPtc12Members(SpaceSystem spaceSystem, AggregateDataType.Builder<?> aggrb, int pfc) {
        boolean argument = aggrb instanceof AggregateArgumentType.Builder;

        aggrb.addMember(new Member("ccsds_version", getIntegerDataType(spaceSystem, 3, argument)));
        aggrb.addMember(new Member("ccsds_packettype", getIntegerDataType(spaceSystem, 1, argument)));
        aggrb.addMember(new Member("ccsds_shflag", getIntegerDataType(spaceSystem, 1, argument)));
        aggrb.addMember(new Member(PARA_NAME_APID, getIntegerDataType(spaceSystem, 11, argument)));
        aggrb.addMember(new Member("ccsds_seqcount", getIntegerDataType(spaceSystem, 16, argument)));
        aggrb.addMember(new Member("ccsds_length", getIntegerDataType(spaceSystem, 16, argument)));
        if (pfc == 0) { // TM packet
            aggrb.addMember(new Member("pus_version", getIntegerDataType(spaceSystem, 4, argument)));
            aggrb.addMember(new Member("pus_time_ref", getIntegerDataType(spaceSystem, 4, argument)));
            aggrb.addMember(new Member(PARA_NAME_PUS_TYPE, getIntegerDataType(spaceSystem, 8, argument)));
            aggrb.addMember(new Member(PARA_NAME_PUS_STYPE, getIntegerDataType(spaceSystem, 8, argument)));
            aggrb.addMember(new Member("pus_counter", getIntegerDataType(spaceSystem, 16, argument)));
            aggrb.addMember(new Member("pus_destination_id", getIntegerDataType(spaceSystem, 16, argument)));
            aggrb.addMember(new Member("pus_time", getIntegerDataType(spaceSystem, 64, argument)));
        } else { // TC packet
            aggrb.addMember(new Member("pus_version", getIntegerDataType(spaceSystem, 4, argument)));
            aggrb.addMember(new Member("pus_ackflags", getIntegerDataType(spaceSystem, 4, argument)));
            aggrb.addMember(new Member(PARA_NAME_PUS_TYPE, getIntegerDataType(spaceSystem, 8, argument)));
            aggrb.addMember(new Member(PARA_NAME_PUS_STYPE, getIntegerDataType(spaceSystem, 8, argument)));
            aggrb.addMember(new Member("pus_source_id", getIntegerDataType(spaceSystem, 16, argument)));
        }
    }

    static int sizeInBits(int ptc, int pfc) {
        if (ptc == 1) {
            return switch (pfc) {
            case 0 -> 1;
            default -> -1;
            };
        } else if (ptc == 2) {
            return pfc;
        } else if (ptc == 3 || ptc == 4) {
            if (pfc < 13) {
                return pfc + 4;
            } else if (pfc == 13) {
                return 24;
            } else if (pfc == 14) {
                return 32;
            } else if (pfc == 15) {
                return 48;
            } else if (pfc == 16) {
                return 64;
            }
        } else if (ptc == 5) {
            return switch (pfc) {
            case 1 -> 32;
            case 2 -> 64;
            case 3 -> 32;
            case 4 -> 48;
            default -> -1;
            };
        } else if (ptc == 6) {
            if (pfc == 0) {
                return -1;
            } else {
                return pfc;
            }
        } else if (ptc == 7 || ptc == 8) {
            if (pfc == 0) {
                return -1;
            } else {
                return pfc * 8;
            }
        } else if (ptc == 9) {
            return switch (pfc) {
            case 1 -> 48;
            case 2 -> 64;
            case 3 -> 8;
            case 4 -> 16;
            case 5 -> 24;
            case 6 -> 32;
            case 7 -> 16;
            case 8 -> 24;
            case 9 -> 32;
            case 10 -> 40;
            case 11 -> 24;
            case 12 -> 32;
            case 13 -> 40;
            case 14 -> 48;
            case 15 -> 32;
            case 16 -> 40;
            case 17 -> 48;
            case 18 -> 56;
            case 30 -> 64;
            default -> -1;
            };
        } else if (ptc == 10) {
            return switch (pfc) {
            case 3 -> 8;
            case 4 -> 16;
            case 5 -> 24;
            case 6 -> 32;
            case 7 -> 16;
            case 8 -> 24;
            case 9 -> 32;
            case 10 -> 40;
            case 11 -> 24;
            case 12 -> 32;
            case 13 -> 40;
            case 14 -> 48;
            case 15 -> 32;
            case 16 -> 40;
            case 17 -> 48;
            case 18 -> 56;
            default -> -1;
            };

        } else if (ptc == 11) {
            if (pfc == 0) {
                return -1;
            } else {
                return pfc;
            }
        }

        return -1;
    }
}
