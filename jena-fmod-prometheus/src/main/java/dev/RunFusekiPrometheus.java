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

package dev;

import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.system.FusekiLogging;
import org.apache.jena.http.HttpOp;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.DatasetGraphFactory;
import org.apache.jena.sparql.exec.http.GSP;
import org.apache.jena.sparql.exec.http.Params;

public class RunFusekiPrometheus {
    public static void main(String ... a) {

        FusekiLogging.setLogging();
        //FusekiModules.add(new FMod_Admin());
        DatasetGraph dsg = DatasetGraphFactory.createTxnMem();
        FusekiServer server = FusekiServer.create()
                //.verbose(true)
                .add("/ds", dsg)
                .port(0)
                .build()
                .start();

        String HOST = "localhost";
        GSP.service("http://"+HOST+":"+server.getHttpPort()+"/ds").defaultGraph().GET();

        //http://localhost:3030/$/datasets
        // Body: dbName=%2Fds&dbType=mem

        // Create!
        Params params = Params.create().add("dbName", "/ds2").add("dbType", "mem");
        String baseURL = "http://"+HOST+":"+server.getHttpPort();

        String x = HttpOp.httpPostRtnString(baseURL+"/$/metrics");
        System.out.println(x);
    }
}
