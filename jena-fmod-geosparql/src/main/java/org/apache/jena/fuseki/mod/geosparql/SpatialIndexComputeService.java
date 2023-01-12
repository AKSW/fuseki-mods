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

import org.apache.jena.atlas.lib.DateTimeUtils;
import org.apache.jena.fuseki.FusekiException;
import org.apache.jena.fuseki.servlets.BaseActionREST;
import org.apache.jena.fuseki.servlets.GraphTarget;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.servlets.ServletOps;
import org.apache.jena.geosparql.spatial.SpatialIndex;
import org.apache.jena.geosparql.spatial.SpatialIndexException;
import org.apache.jena.query.Dataset;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.riot.WebContent;
import org.apache.jena.riot.web.HttpNames;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.web.HttpSC;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static java.lang.String.format;
import static org.apache.jena.fuseki.servlets.GraphTarget.determineTarget;

/**
 * Spatial index (re)computation service.
 */
public class SpatialIndexComputeService extends BaseActionREST { //ActionREST {

    public SpatialIndexComputeService() {}

    private static List<String> getGraphs(DatasetGraph dsg, HttpAction action) {
        String[] uris = action.getRequest().getParameterValues(HttpNames.paramGraph);

        return List.of(uris);
    }

    @Override
    protected void doPost(HttpAction action) {

        String spatialIndexFilePathStr = action.getRequestParameter("spatial-index-file");

        String commit = action.getRequestParameter("commit");

        DatasetGraph dsg = action.getDataset();

        action.beginRead();
//        GraphTarget graphTarget = determineTarget(dsg, action);
        List<String> graphs = getGraphs(dsg, action);
//        if (!graphTarget.exists())
//            ServletOps.errorNotFound("No data graph: " + graphTarget.label());
        action.end();

        Dataset ds = DatasetFactory.wrap(dsg);
        try {
            SpatialIndex index = ds.getContext().get(SpatialIndex.SPATIAL_INDEX_SYMBOL);

            if (index == null) { // no spatial index has been configured
                action.log.error(format("[%d] no spatial index has been configured for the dataset", action.id));
            } else {
                action.log.info(format("[%d] spatial index: computation started", action.id));

                boolean spatialIndexPerGraph = ds.getContext().get(SpatialIndex.symSpatialIndexPerGraph, false);
                if (!spatialIndexPerGraph) {
                    action.log.info(format("[%d] (re)computing full spatial index as single index tree", action.id));
                    index = SpatialIndex.buildSpatialIndex(ds, index.getSrsInfo().getSrsURI(), false);
                } else {
                    boolean isUnionGraph = graphs.contains(HttpNames.graphTargetUnion);
                    if (isUnionGraph) { // union graph means we compute the whole index
                        action.log.info(format("[%d] (re)computing full spatial index as separate index trees", action.id));
                        index = SpatialIndex.buildSpatialIndex(ds, index.getSrsInfo().getSrsURI(), true);
                    } else {
                        action.log.info(format("[%d] (re)computing spatial index for graphs {}", action.id), graphs);
                        index = SpatialIndex.recomputeIndexForGraphs(index, ds, graphs);
                    }
                }

                if (commit != null) {
                    File targetFile;
                    if (spatialIndexFilePathStr != null) {
                        targetFile = new File(spatialIndexFilePathStr);
                    } else {
                        targetFile = index.getLocation();
                    }
                    if (targetFile != null) {
                        action.log.info("writing spatial index to disk at {}", targetFile.getAbsolutePath());
                        SpatialIndex.save(targetFile, index);
                    } else {
                        action.log.warn("Skipping write: Spatial index write requested, but the spatial index was configured without a file location" +
                                " and no file param has been provided to the request neither. Skipping");
                    }

                }
            }

        } catch (SpatialIndexException e) {
            throw new RuntimeException(e);
        }

        action.log.info(format("[%d] spatial index: computation finished", action.id));
        action.setResponseStatus(HttpSC.OK_200);
        action.setResponseContentType(WebContent.contentTypeTextPlain);
        try {
            action.getResponseOutputStream().print("Spatial index computation completed at " + DateTimeUtils.nowAsXSDDateTimeString());
        } catch (IOException e) {
            throw new FusekiException(e);
        }
    }
}
