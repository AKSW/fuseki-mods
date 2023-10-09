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

package org.apache.jena.fuseki.mod.graphql;

import static java.lang.String.format;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.aksw.jenax.dataaccess.sparql.datasource.RdfDataSource;
import org.aksw.jenax.dataaccess.sparql.factory.dataengine.RdfDataEngines;
import org.aksw.jenax.graphql.GraphQlExec;
import org.aksw.jenax.graphql.GraphQlExecFactory;
import org.aksw.jenax.graphql.impl.core.GraphQlExecUtils;
import org.aksw.jenax.graphql.impl.core.GraphQlResolverAlwaysFail;
import org.aksw.jenax.graphql.impl.sparql.GraphQlExecFactoryOverSparql;
import org.aksw.jenax.graphql.impl.sparql.GraphQlToSparqlConverter;
import org.apache.commons.io.IOUtils;
import org.apache.jena.fuseki.FusekiException;
import org.apache.jena.fuseki.servlets.BaseActionREST;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.query.DatasetFactory;
import org.apache.jena.riot.WebContent;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.web.HttpSC;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import com.google.gson.JsonObject;

/**
 * Spatial index (re)computation service.
 */
public class GraphQlQueryService extends BaseActionREST { //ActionREST {

    @Override
    protected void doGet(HttpAction action) {
        // Serves the minimal graphql ui
        String resourceName = "graphql/mui/index.html";
        String str = null;
        try (InputStream in = GraphQlQueryService.class.getClassLoader().getResourceAsStream(resourceName)) {
            str = IOUtils.toString(in, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new FusekiException(e);
        }

        if (str == null) {
            action.setResponseStatus(HttpSC.INTERNAL_SERVER_ERROR_500);
            action.setResponseContentType(WebContent.contentTypeTextPlain);
            str = "Failed to load classpath resource " + resourceName;
        } else {
            action.setResponseStatus(HttpSC.OK_200);
            action.setResponseContentType(WebContent.contentTypeHTML);
        }
        try (OutputStream out = action.getResponseOutputStream()) {
            IOUtils.write(str, out, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new FusekiException(e);
        }
    }

    @Override
    protected void doPost(HttpAction action) {
        DatasetGraph dsg = action.getDataset();
        Preconditions.checkArgument(dsg != null, "DatasetGraph not set for request");

        String queryJsonStr;
        try (InputStream in = action.getRequestInputStream()) {
            queryJsonStr = IOUtils.toString(in, StandardCharsets.UTF_8);
        } catch (IOException e1) {
            throw new FusekiException(e1);
        }
        Gson gson = new Gson();
        JsonObject queryJson = gson.fromJson(queryJsonStr, JsonObject.class);

        RdfDataSource dataSource = RdfDataEngines.of(DatasetFactory.wrap(dsg));
        GraphQlExecFactory gef = new GraphQlExecFactoryOverSparql(dataSource,
                new GraphQlToSparqlConverter(new GraphQlResolverAlwaysFail()));
        GraphQlExec ge = GraphQlExecUtils.execJson(gef, queryJson);

        action.beginRead();
        try {
            action.setResponseStatus(HttpSC.OK_200);
            action.setResponseContentType(WebContent.contentTypeJSON);
            try (OutputStream out = action.getResponseOutputStream()) {
                GraphQlExecUtils.writePretty(out, ge);
            }
            // action.log.info(format("[%d] graphql: execution finished", action.id));
        } catch (IOException e) {
            throw new FusekiException(e);
        } finally {
            action.end();
        }
    }
}