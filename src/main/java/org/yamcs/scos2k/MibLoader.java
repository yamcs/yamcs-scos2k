package org.yamcs.scos2k;

import org.yamcs.ConfigurationException;
import org.yamcs.YConfiguration;
import org.yamcs.scos2k.MibLoaderBits.MibLoadException;
import org.yamcs.mdb.DatabaseLoadException;
import org.yamcs.xtce.Header;
import org.yamcs.xtce.SpaceSystem;

/**
 * Loads SCOS MIB database.
 * 
 * It's split in four files: MibLoader, TcMibLoader, TmMibLoader and BaseMibLoader
 */
public class MibLoader extends TcMibLoader {
    

    public MibLoader(YConfiguration config) throws ConfigurationException {
        super(config);
    }
    
    @Override
    public SpaceSystem load() throws ConfigurationException, DatabaseLoadException {
        log.debug("Loading SCOS2000 MIB from {}", path);
        loadVdf();
        loadTelemetry();
        loadCommands();
        return spaceSystem;
    }
    
    // Database version
    final static int IDX_VDF_NAME = 0;
    final static int IDX_VDF_COMMENT = 1;
    final static int IDX_VDF_DOMAINID = 2;
    final static int IDX_VDF_RELEASE = 3;
    final static int IDX_VDF_ISSUE = 4;
    private void loadVdf() {
        switchTo("vdf");
        String[] line = null;
        String[] lastLine = null;
        while ((line = nextLine()) != null) {
            checkMandatory(line, IDX_VDF_NAME);
            lastLine = line;
        }
        if (lastLine == null) {
            throw new MibLoadException(ctx, "No record present into the VDF table");
        }

        spaceSystem = new SpaceSystem(ssName);
        Header h = new Header();
        int release = getInt(lastLine, IDX_VDF_RELEASE, 0);
        int issue = getInt(lastLine, IDX_VDF_ISSUE, 0);
        h.setVersion(release + "." + issue);
        spaceSystem.setHeader(h);
        if (hasColumn(lastLine, IDX_VDF_COMMENT)) {
            spaceSystem.setShortDescription(lastLine[IDX_VDF_COMMENT]);
        }
    }
}
