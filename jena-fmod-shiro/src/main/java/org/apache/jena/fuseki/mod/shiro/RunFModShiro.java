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

package org.apache.jena.fuseki.mod.shiro;

import java.net.URI;

import org.apache.jena.atlas.lib.Lib;
import org.apache.jena.atlas.web.HttpException;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.http.auth.AuthEnv;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.exec.http.GSP;
import org.apache.jena.web.HttpSC;

public class RunFModShiro {

    public static void main(String[] args) {
        try {
            main2();
        } catch (Throwable th) {
            th.printStackTrace();
        } finally {
            System.exit(0);
        }
    }

    public static void main2() {
        //Automatic: FusekiModules.add(new FMod_Shiro());
        FusekiLogging.setLogging();
        // TODO Proper append path
        System.setProperty("FUSEKI_BASE", "src/test/files/");
        //System.setProperty("FUSEKI_SHIRO", "disabled");

        FusekiServer server = FusekiServer.create()
                .port(0)
                .add("/ds", DatasetGraphFactory.createTxnMem())
                .start();

        String URL = "http://localhost:"+server.getPort()+"/ds";

        AuthEnv.get().registerUsernamePassword(URI.create(URL), "user1", "passwd1");
        GSP.service(URL).defaultGraph().GET();

        AuthEnv.get().clearAuthEnv();
        try {
            AuthEnv.get().registerUsernamePassword(URI.create(URL), "user1", "passwd2");
            GSP.service(URL).defaultGraph().GET();
            System.err.println("Bad ** Expected  401");
        } catch (HttpException httpEx) {
            if ( httpEx.getStatusCode() == HttpSC.UNAUTHORIZED_401 ) {
                System.err.println("401 (as expected)");
            } else
                throw httpEx;
        }

        System.err.flush();
        Lib.sleep(100);
        System.out.println("DONE");
        System.exit(0);
    }

}
