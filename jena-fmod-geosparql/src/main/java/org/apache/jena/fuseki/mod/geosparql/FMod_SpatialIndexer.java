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
package org.apache.jena.fuseki.mod.geosparql;

import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.fuseki.server.*;
import org.apache.jena.rdf.model.Model;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class FMod_SpatialIndexer implements FusekiModule {

    private Operation spatialOperation = null;

    @Override
    public String name() {
        return "Spatial Indexer";
    }

    @Override
    public void start() {
        Fuseki.configLog.info("Add spatial indexer operation into global registry.");
        spatialOperation = Operation.alloc("http://org.apache.jena/spatial-index-service",
                "spatial-indexer",
                "Spatial index computation service");
    }

    @Override
    public void prepare(FusekiServer.Builder builder, Set<String> datasetNames, Model configModel) {
        Fuseki.configLog.info("Module adds spatial index servlet");
        builder.registerOperation(spatialOperation, new SpatialIndexComputeService());
        datasetNames.forEach(name -> builder.addEndpoint(name, "spatial", spatialOperation));
    }

    @Override
    public void configured(FusekiServer.Builder serverBuilder, DataAccessPointRegistry dapRegistry, Model configModel) {
        FusekiModule.super.configured(serverBuilder, dapRegistry, configModel);

        List<DataAccessPoint> daps = dapRegistry.accessPoints().stream().map(dap -> {
            Endpoint endpoint = Endpoint.create()
                    .operation(spatialOperation)
                    .endpointName("spatial")
                    .build();
            // create new DataService based on existing one with the endpoint attached
            DataService dSrv = DataService.newBuilder(dap.getDataService()).addEndpoint(endpoint).build();
            return new DataAccessPoint(dap.getName(), dSrv);
        }).collect(Collectors.toList());

        // "replace" each DataAccessPoint
        daps.forEach(dap -> {
            dapRegistry.remove(dap.getName());
            dapRegistry.register(dap);
        });
    }

    @Override
    public void configDataAccessPoint(DataAccessPoint dap, Model configModel) {
        FusekiModule.super.configDataAccessPoint(dap, configModel);
    }

    @Override
    public void serverAfterStarting(FusekiServer server) {
        Fuseki.configLog.info("Customized server start on port " + server.getHttpPort());
    }
}
