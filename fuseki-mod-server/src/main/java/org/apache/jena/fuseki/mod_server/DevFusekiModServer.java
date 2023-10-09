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

package org.apache.jena.fuseki.mod_server;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest.BodyPublishers;
import java.nio.charset.StandardCharsets;

import org.apache.jena.atlas.lib.FileOps;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModules;
import org.apache.jena.fuseki.mod.admin.FMod_Admin;
import org.apache.jena.fuseki.mod.ui.FMod_UI;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.http.HttpOp;
import org.apache.jena.http.auth.AuthEnv;
import org.apache.jena.rdflink.RDFLink;
import org.apache.jena.riot.WebContent;
import org.apache.jena.sparql.exec.RowSet;
import org.apache.jena.sparql.exec.RowSetOps;
import org.apache.jena.sys.JenaSystem;

public class DevFusekiModServer {

    static { FusekiLogging.setLogging(); }

    public static void main(String[] args) {



//        07:57:43 INFO  Server          :: Module: FMod_UI (unknown)
//        07:57:43 INFO  Server          :: Module: FMod_Admin (unknown)
//        07:57:43 INFO  Server          :: Module: FMod_Shiro (unknown)
//        07:57:43 INFO  Config          :: Fuseki UI load
//        07:57:43 INFO  FMod_UI         :: UI Base = jar:file:/home/afs/.m2/repository/org/apache/jena/jena-fuseki-ui/4.9.0-SNAPSHOT/jena-fuseki-ui-4.9.0-SNAPSHOT.jar!/webapp
//        07:57:43 INFO  Config          :: Fuseki UI loaded --<
//        07:57:43 INFO  Config          :: Fuseki Admin load

//        JenaSystem.DEBUG_INIT = true;
//        //System.setProperty("fuseki.loglogging", "true");

        //System.setProperty("fuseki.logLoading", "true");

        JenaSystem.init();

//        // Order matters
        FusekiModules fmods = FusekiModules.create(FMod_UI.get(), FMod_Admin.get());

        System.setProperty("FUSEKI_BASE", "run");
        FileOps.clearAll("run");

        System.err.println("Re-enable!");

        // Auto-discovery also works after maven has run.
        //FusekiModulesCtl.setup();

        // Need to sort out /run/ initial source
        FusekiServer server = FusekiServer.create()
                //.setFusekiModules(fmods)
                .port(0)
                //.add("/ds", DatasetGraphFactory.createTxnMem())
                .start();

        String datasetURL = server.serverURL()+"ds";

        String serverURL = server.serverURL()+"$/datasets";
        String name = URLEncoder.encode("/ds", StandardCharsets.UTF_8);
        System.out.println(serverURL);

        // Create dataset
        //http://localhost:4040/$/datasets
        //dbName=%2Fds&dbType=mem

        // basic auth. admin-pw
        String adminURL = server.serverURL()+"$";
        AuthEnv.get().registerUsernamePassword(URI.create(adminURL), "admin","pw");

        HttpOp.httpPost(adminURL+"/datasets",
                        WebContent.contentTypeHTMLForm,
                        BodyPublishers.ofString("dbName="+name+"&dbType=mem", StandardCharsets.UTF_8));

        try ( RDFLink link = RDFLink.connectPW(datasetURL, "user1", "passwd1") ) {
            link.update("INSERT DATA { <x:s> <x:p> 123 }");
            RowSet rs = link.query("SELECT * {?s ?p ?o}").select();
            RowSetOps.out(rs);
        }

        if ( false ) {
            try {
                server.join();
            } catch (Throwable th) {
                th.printStackTrace();
            } finally {
                System.exit(0);
            }
        }
    }
}
