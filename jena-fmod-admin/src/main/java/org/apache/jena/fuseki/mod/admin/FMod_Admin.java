/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.jena.fuseki.mod.admin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.cmd.ArgModuleGeneral;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.build.FusekiConfig;
import org.apache.jena.fuseki.ctl.ActionCtl;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.cmds.FusekiMain;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.fuseki.mgt.ActionBackup;
import org.apache.jena.fuseki.mgt.ActionBackupList;
import org.apache.jena.fuseki.mgt.ActionDatasets;
import org.apache.jena.fuseki.mod.other.ActionServerStatus;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;

public class FMod_Admin implements FusekiModule {

    public FMod_Admin() {}

    private static Logger LOG = Fuseki.configLog;

    @Override
    public void start() {
        ArgModuleGeneral amg = new ArgModuleAdmin();
        FusekiMain.addArgModule(amg);
    }

    // Modules add command line args?

    // [ ] Startup - inject commands. ArgModuleAdmin.
    // Future - disk builds a configuration.
    // [ ] Localhost - put in ActionCtl.
    // [ ] Split "datasets" into read and write functions. Or test
    // [ ] AdminAllowed:
    // "--admin user=password" or "--admin local"
    // "--adminArea run/"

    // [ ] Modules and server startup / command line.
    // ArgModule!

    @Override
    public String name() {
        return "Admin";
    }

    @Override
    public int level() {
        return 600;
    }

    @Override
    public void prepare(FusekiServer.Builder builder, Set<String> datasetNames, Model configModel) {
        FusekiApp.setup();
        String configDir = FusekiApp.dirConfiguration.toString();
        List<DataAccessPoint> directoryDatabases = FusekiConfig.readConfigurationDirectory(configDir);

        Path serverConfigPath = FusekiApp.FUSEKI_BASE.resolve(FusekiApp.DFT_CONFIG).toAbsolutePath();
        if ( Files.exists(serverConfigPath) ) {
            LOG.info("Fuseki Admin loading server config " + serverConfigPath);
            builder.parseConfigFile(serverConfigPath.toString());
        } else {
            LOG.warn("Fuseki Admin server config not found " + serverConfigPath);
        }

        if ( directoryDatabases.isEmpty() )
            FmtLog.info(Fuseki.configLog, "No databases: dir=%s", configDir);
        else {
            directoryDatabases.forEach(dap -> FmtLog.info(Fuseki.configLog, "Database: %s", dap.getName()));
        }

        directoryDatabases.forEach(db -> {
            String dbName = db.getName();
            if ( datasetNames.contains(dbName) ) {
                Fuseki.configLog.warn(String.format("Database '%s' already added to the Fuseki server builder", dbName));
                // ?? builder.remove(dbName);
            }
            builder.add(dbName, db.getDataService());
            // ** builder.add(DataAccessPoint);
        });

        // Modify the server to include the admin operations.
        ActionCtl actionBackup = new ActionBackup();
        builder
                // // Before the Fuseki filter!
                // .addFilter("/$/*", new LocalhostOnly())

                .addServlet("/$/datasets", new ActionDatasets())
                // .addServlet("/$/stats", new ActionDatasets())
                .addServlet("/$/server", new ActionServerStatus())

                // Require admin user
                .addServlet("/$/backup", actionBackup).addServlet("/$/backups", actionBackup)
                .addServlet("/$/backups-list", new ActionBackupList())

                .enablePing(true)
                .enableStats(true)
                // Not required but helpful.
                .enableCompact(true)
                .enableMetrics(true)
                .enableTasks(true);

        LOG.info("Fuseki Admin loaded");
    }
}
