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

import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;

public class DevFusekiModServer {

    public static void main(String[] args) {
        FusekiLogging.setLogging();
        // Shiro
        System.setProperty("FUSEKI_BASE", "run");

        FusekiServer server = FusekiServer.create()
                .port(4040)
                //.add("/ds", DatasetGraphFactory.createTxnMem())
                .start();

        String URL = "http://localhost:"+server.getPort()+"/ds";
        //AuthEnv.get().registerUsernamePassword(URI.create(URL), "user1", "passwd1");

        try {
            server.join();
        } catch (Throwable th) {
            th.printStackTrace();
        } finally {
            System.exit(0);
        }
    }

}
