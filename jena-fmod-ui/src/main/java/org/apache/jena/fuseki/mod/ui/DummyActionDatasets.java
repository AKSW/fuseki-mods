/**
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

package org.apache.jena.fuseki.mod.ui;

import static java.lang.String.format;

import org.apache.jena.atlas.json.JsonBuilder;
import org.apache.jena.atlas.json.JsonObject;
import org.apache.jena.atlas.json.JsonValue;
import org.apache.jena.fuseki.ctl.ActionContainerItem;
import org.apache.jena.fuseki.ctl.JsonDescription;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.FusekiVocab;
import org.apache.jena.fuseki.server.ServerConst;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.servlets.ServletOps;
import org.apache.jena.rdf.model.Property;

public class DummyActionDatasets extends ActionContainerItem {

    static private Property pServiceName = FusekiVocab.pServiceName;
    //static private Property pStatus = FusekiVocab.pStatus;

    private static final String paramDatasetName    = "dbName";
    private static final String paramDatasetType    = "dbType";
    private static final String tDatabaseTDB        = "tdb";
    private static final String tDatabaseTDB2       = "tdb2";
    private static final String tDatabaseMem        = "mem";

    public DummyActionDatasets() { super(); }

    @Override
    public void validate(HttpAction action) {}

    // ---- GET : return details of dataset or datasets.
    @Override
    protected JsonValue execGetContainer(HttpAction action) {
        action.log.info(format("[%d] GET datasets", action.id));
        JsonBuilder builder = new JsonBuilder();
        builder.startObject("D");
        builder.key(ServerConst.datasets);
        JsonDescription.arrayDatasets(builder, action.getDataAccessPointRegistry());
        builder.finishObject("D");
        return builder.build();
    }

    @Override
    protected JsonValue execGetItem(HttpAction action) {
        String item = getItemDatasetName(action);
        action.log.info(format("[%d] GET dataset %s", action.id, item));
        JsonBuilder builder = new JsonBuilder();
        DataAccessPoint dsDesc = getItemDataAccessPoint(action, item);
        if ( dsDesc == null )
            ServletOps.errorNotFound("Not found: dataset "+item);
        JsonDescription.describe(builder, dsDesc);
        return builder.build();
    }

    private static final JsonValue EmptyObject = new JsonObject();

    // ---- POST

    @Override
    protected JsonValue execPostContainer(HttpAction action) {
        ServletOps.errorMethodNotAllowed("POST", "jena-fmod-admin not installed");
        return EmptyObject;
    }

    @Override
    protected JsonValue execPostItem(HttpAction action) {
        ServletOps.errorMethodNotAllowed("POST", "jena-fmod-admin not installed");
        return EmptyObject;
    }

    // ---- DELETE
    // Not allowed.

}
