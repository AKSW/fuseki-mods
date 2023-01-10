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
import java.util.Set;

import org.apache.jena.cmd.ArgDecl;
import org.apache.jena.cmd.ArgModuleGeneral;
import org.apache.jena.cmd.CmdArgModule;
import org.apache.jena.cmd.CmdGeneral;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.FusekiConfigException;
import org.apache.jena.fuseki.build.FusekiConfig;
import org.apache.jena.fuseki.ctl.ActionCtl;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.cmds.FusekiMain;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.fuseki.mgt.ActionBackup;
import org.apache.jena.fuseki.mgt.ActionBackupList;
import org.apache.jena.fuseki.mgt.ActionDatasets;
import org.apache.jena.fuseki.mod.other.ActionServerStatus;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.webapp.FusekiEnv;
import org.apache.jena.fuseki.webapp.FusekiWebapp;
import org.apache.jena.rdf.model.Model;
import org.slf4j.Logger;

public class FMod_Admin implements FusekiModule {

    public FMod_Admin() {}

    private static Logger LOG = Fuseki.configLog;

    @Override
    public void start() {
        // Code - doesn't matter.
        FusekiEnv.FUSEKI_HOME = null;

        // --base is the static file area.

        // --run=
        FusekiEnv.FUSEKI_BASE = null;
        // Accessible?
        // Writable?

        FusekiEnv.mode = FusekiEnv.INIT.STANDALONE;
        FusekiWebapp.formatBaseArea();

        ArgModuleGeneral amg = new ArgModuleAdmin();
        FusekiMain.addArgModule(amg);
    }

    // Modules add command line args?

    // [ ] Startup - inject commands. ArgModuleAdmin.
    // Future - disk builds a configuration.
    // [ ] Localhost - put in ActionCtl.
    // [ ] Split "datasets" into read and write functions. Or test
    // [ ] AdminAllowed:
    //     "--admin user=password" or "--admin local"
    //     "--adminArea run/"

    // [ ] Modules and server startup / command line.
    //     ArgModule!

    public static class ArgModuleAdmin implements ArgModuleGeneral {
        // Add a static of "extra command"

        private ArgDecl argAdmin = new ArgDecl(true, "admin");
        private ArgDecl argAdminArea = new ArgDecl(true, "adminArea", "adminBase");

        public ArgModuleAdmin() { }

        @Override
        public void processArgs(CmdArgModule cmdLine) {
            String admin = cmdLine.getValue(argAdmin);
            if ( admin != null ) {
                if ( admin.equals("localhost") ) {}
                else {
                    String pwFile = admin;
                }
            }

            String dirStr = cmdLine.getValue(argAdminArea);
            Path directory = Path.of(dirStr);

            if ( ! Files.isDirectory(directory) )
                throw new FusekiConfigException("Not a directory: "+dirStr);

            if ( ! Files.isWritable(directory)  )
                throw new FusekiConfigException("Not writable: "+dirStr);

            FusekiEnv.FUSEKI_BASE = directory;
        }

        @Override
        public void registerWith(CmdGeneral cmdLine) {
            cmdLine.add(argAdmin,     "--admin=[UserPasswordFile|localhost]", "Enable the admin module");
            cmdLine.add(argAdminArea, "--run=DIR", "Admin state directory");
        }
    }


    @Override
    public String name() {
        return "Admin";
    }

    @Override
    public void prepare(FusekiServer.Builder builder, Set<String> datasetNames, Model configModel) {
        // Modify the server to include the admin operations.
        ActionCtl actionBackup = new ActionBackup();
        builder
//            // Before the Fuseki filter!
//            .addFilter("/$/*", new LocalhostOnly())

            // Information
            .addServlet("/$/datasets", new ActionDatasets())
            //.addServlet("/$/stats", new ActionDatasets())
            .addServlet("/$/server", new ActionServerStatus())

            // Require admin user
            .addServlet("/$/backup", actionBackup)
            .addServlet("/$/backups", actionBackup)
            .addServlet("/$/backups-list", new ActionBackupList())

            // General server functions.
            //.addServlet("/$/datasets", new ActionDatasets()) -- UI has a restricted version

            .enablePing(true)
            .enableStats(true)
            // Not required but helpful.
            .enableCompact(true)
            .enableMetrics(true)
            .enableTasks(true)
            ;

        FusekiConfig.readConfigurationDirectory(FusekiWebapp.dirConfiguration.toString())
                .forEach((dap) -> builder.add(dap.getName(), DataService.newBuilder(dap.getDataService())));

        LOG.info("Fuseki Admin loaded");
    }
}
