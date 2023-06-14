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

package org.apache.jena.fuseki.mod.ui;

import java.util.Set;

import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.fuseki.validation.DataValidator;
import org.apache.jena.fuseki.validation.IRIValidator;
import org.apache.jena.fuseki.validation.QueryValidator;
import org.apache.jena.fuseki.validation.UpdateValidator;
import org.apache.jena.rdf.model.Model;
import org.eclipse.jetty.util.resource.Resource;
import org.slf4j.Logger;

public class FMod_UI implements FusekiModule {

    public FMod_UI() {}

    private static Logger LOG = Fuseki.configLog;

    @Override
    public String name() {
        return "UI";
    }

    // Before FMod_admin
    @Override
    public int level() { return 500; }

    @Override
    public void prepare(FusekiServer.Builder builder, Set<String> datasetNames, Model configModel) {
        if ( builder.staticFileBase() != null ) {
            LOG.info("Static content location has already been set: "+builder.staticFileBase());
            return;
        }

        // Find the static content and set all resource lookups to this location.

        String resourceNameUI = "webapp";
        org.eclipse.jetty.util.resource.Resource uiApp = Resource.newClassPathResource(resourceNameUI);
        //jar:file:/home/afs/ASF/fuseki-mods/fuseki-mod-ui/jena-fuseki-ui.jar!/webapp

        if ( uiApp == null ) {
            LOG.warn("Can't locate the UI static resources");
            return;
        }

        String uiAppLocation = uiApp.toString();
        FmtLog.info(this.getClass(), "UI Base = %s", uiAppLocation);

        // Modify the server to include the UI and any server-side servlets needed by the UI.
        builder
            .staticFileBase(uiAppLocation)
            // Required functions. Dummies.
            // Not database create.
            .addServlet("/$/datasets", new DummyActionDatasets())
            .addServlet("/$/server", new DummyActionServerStatus())

            .enablePing(true)
            .enableStats(true)
            // Not required but helpful.
            .enableCompact(true)
            .enableMetrics(true)
            .enableTasks(true)
            // Validators (for "/sparqler/")
            .addServlet("/$/validate/query",  new QueryValidator())
            .addServlet("/$/validate/update", new UpdateValidator())
            .addServlet("/$/validate/iri",    new IRIValidator())
            .addServlet("/$/validate/data",   new DataValidator())
            ;

        LOG.info("Fuseki UI loaded");
    }
}
