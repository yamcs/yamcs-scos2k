package org.yamcs.scos2k;

import java.io.IOException;
import java.io.LineNumberReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.yamcs.AbstractYamcsService;
import org.yamcs.InitException;
import org.yamcs.Spec;
import org.yamcs.YConfiguration;
import org.yamcs.YamcsServer;
import org.yamcs.Spec.OptionType;
import org.yamcs.plists.ParameterList;
import org.yamcs.plists.ParameterListDb;
import org.yamcs.plists.ParameterListService;
import org.yamcs.yarch.DataType;
import org.yamcs.yarch.Tuple;
import org.yamcs.yarch.YarchDatabase;
import org.yamcs.yarch.YarchDatabaseInstance;
import org.yamcs.yarch.streamsql.StreamSqlResult;

/**
 * Loads the SCOS2K alphanumeric displays (dpf.dat and dpc.dat) into Yamcs parameter lists
 */
public class MibDisplayLoader extends AbstractYamcsService {
    Path mibPath;
    ParameterListService plistService;
    String mibQualifiedName;

    // this column is used to mark the parameter lists that are loaded from MIB
    public static final String CNAME_MIB = "scos2k_mib";

    @Override
    public void init(String yamcsInstance, String serviceName, YConfiguration config) throws InitException {
        super.init(yamcsInstance, serviceName, config);
        this.mibPath = Paths.get(config.getString("mibPath"));
        this.mibQualifiedName = config.getString("mibQualifiedName");
        if (!mibQualifiedName.endsWith("/")) {
            mibQualifiedName += "/";
        }

        if (!Files.exists(mibPath)) {
            throw new InitException(mibPath + " does not exist");
        }
        if (!Files.isDirectory(mibPath)) {
            throw new InitException(mibPath + " is not a directory");
        }
        this.plistService = getService();
    }

    @Override
    protected void doStart() {
        // load the displays in a background thread
        Thread thread = new Thread(() -> {
            try {
                loadAlphanumericDisplays();
            } catch (IOException e) {
                log.warn("Error loading the alphanumeric displays ", e);
            }
        });

        thread.start();
        notifyStarted();
    }

    @Override
    protected void doStop() {
        notifyStopped();
    }

    private void loadAlphanumericDisplays() throws IOException {
        List<MibParameterList> existingPlists = new ArrayList<>();
        YarchDatabaseInstance ydb = YarchDatabase.getInstance(yamcsInstance);
        StreamSqlResult r = ydb.executeUnchecked("select * from " + ParameterListDb.TABLE_NAME);
        try {
            while (r.hasNext()) {
                Tuple tuple = r.next();
                if (tuple.hasColumn(CNAME_MIB) && tuple.getBooleanColumn(CNAME_MIB)) {
                    var plist = new MibParameterList(tuple);
                    existingPlists.add(plist);
                }
            }
        } finally {
            r.close();
        }

        var dpc = readDpc();
        Set<UUID> toRemove = new HashSet<>();

        var db = plistService.getParameterListDb();
        for (var plist : existingPlists) {
            var mibDisplay = dpc.remove(plist.getName());

            if (mibDisplay == null) {
                log.debug("{} does not exist anymore in the MIB, it will be removed", plist.getName());
                toRemove.add(plist.getId());
            } else {
                try {
                    List<String> mibList = mibDisplay.plist();
                    if (!plist.getPatterns().equals(mibList)
                            || !Objects.equals(plist.getDescription(), mibDisplay.description)) {
                        plist.setPatterns(mibList);
                        plist.setDescription(mibDisplay.description);
                        log.debug("Updating parameter list {}", plist);
                        db.update(plist);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        for (var mibDisplay : dpc.values()) {
            var plist = new MibParameterList(UUID.randomUUID(), mibDisplay.name, mibDisplay.description,
                    mibDisplay.plist());
            log.debug("Inserting parameter list {}", plist);
            db.insert(plist);
        }

        if (!toRemove.isEmpty()) {
            log.debug("Removing {} parameter lists", toRemove.size());
            for (var id : toRemove) {
                db.delete(id);
            }
        }
    }

    private Map<String, MibDisplay> readDpc() throws IOException {
        var dpc = readDpf();

        try (var dpcReader = new LineNumberReader(Files.newBufferedReader(mibPath.resolve("dpc.dat")))) {
            String[] line;

            while ((line = nextLine(dpcReader)) != null) {
                if (line.length < 3) {
                    log.warn("Ignoring invalid line {} in dpc.dat: {}", dpcReader.getLineNumber(),
                            Arrays.toString(line));
                    continue;
                }
                if (line[1] == null || line[1].isBlank()) {
                    continue;
                }

                var mibDisplay = dpc.get(line[0]);
                if (mibDisplay == null) {
                    log.warn("Line {} in dpc.dat refers to unexisting display '{}', ignoring",
                            dpcReader.getLineNumber(), line[0]);
                    continue;
                }
                int dpcFldn;
                try {
                    dpcFldn = Integer.parseInt(line[2]);
                } catch (IllegalArgumentException e) {
                    throw new IOException("Failed to parse '" + line[2] + "' into a number");
                }
                if (dpcFldn < 0 || dpcFldn >= mibDisplay.params.length) {
                    throw new IOException("Invalid DPC_FLDN on line " + dpcReader.getLineNumber() + ": " + line[2]
                            + "( maximum expected: " + mibDisplay.params.length + ")");
                }
                mibDisplay.params[dpcFldn] = mibQualifiedName + line[1];
            }
        }

        return dpc;
    }

    private Map<String, MibDisplay> readDpf() throws IOException {
        Map<String, MibDisplay> dpfContent = new HashMap<>();

        try (var dpfReader = new LineNumberReader(Files.newBufferedReader(mibPath.resolve("dpf.dat")))) {
            String[] line;

            while ((line = nextLine(dpfReader)) != null) {
                if (line.length < 3) {
                    log.warn("Ignoring invalid line {} in dpf.dat: {}", dpfReader.getLineNumber(),
                            Arrays.toString(line));
                    continue;
                }
                int numParams;
                if ("1".equals(line[1])) {
                    numParams = 32;
                } else if ("3".equals(line[1])) {
                    numParams = 64;
                } else {
                    log.warn("Ignoring invalid line {} in dpf.dat: {}", dpfReader.getLineNumber(),
                            Arrays.toString(line));
                    continue;
                }

                dpfContent.put(line[0], new MibDisplay(line[0], line[2], new String[numParams]));
            }
        }
        return dpfContent;
    }

    private String[] nextLine(LineNumberReader reader) throws IOException {
        String line = reader.readLine();
        if (reader.getLineNumber() == 1 && line != null && line.startsWith("#")) {
            line = reader.readLine();
        }
        if (line == null) {
            return null;
        }
        return line.split("\t");
    }

    public Spec getSpec() {
        Spec spec = new Spec();
        spec.addOption("mibPath", OptionType.STRING).withRequired(true)
                .withDescription("Path on disk to the MIB directory. Used to load the dpf.dat and dpc.dat files.");
        spec.addOption("mibQualifiedName", OptionType.STRING).withDefault("/MIB")
                .withDescription("Fully qualified name of the MIB subsystem in the MDB. Used to reference parameters.");

        return spec;
    }

    public ParameterListService getService() throws InitException {
        var services = YamcsServer.getServer().getInstance(yamcsInstance)
                .getServices(ParameterListService.class);
        if (services.isEmpty()) {
            throw new InitException("No parameter list service found");
        } else {
            if (services.size() > 1) {
                log.warn("Multiple parameter list services found but only one supported");
            }
            return services.get(0);
        }
    }

    record MibDisplay(String name, String description, String[] params) {
        List<String> plist() {
            return Arrays.stream(params)
                    .filter(s -> s != null)
                    .collect(Collectors.toList());
        }
    }

    static class MibParameterList extends ParameterList {

        public MibParameterList(Tuple tuple) {
            super(tuple);
        }

        public MibParameterList(UUID id, String name, String description, List<String> plist) {
            super(id, name);
            setDescription(description);
            setPatterns(plist);
        }

        @Override
        public Tuple toTuple() {
            var tuple = super.toTuple();
            tuple.addColumn(CNAME_MIB, DataType.BOOLEAN, true);
            return tuple;
        }
    }
}
