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

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Objects;
import java.util.Set;

import javax.servlet.Filter;
import javax.servlet.ServletContext;

import org.apache.jena.atlas.lib.Lib;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.FusekiConfigException;
import org.apache.jena.fuseki.main.FusekiServer;
import org.apache.jena.fuseki.main.sys.FusekiModule;
import org.apache.jena.rdf.model.Model;
import org.apache.shiro.web.servlet.ShiroFilter;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fuseki Module for Apache Shiro
 */
public class FMod_Shiro implements FusekiModule {
    // Assumes the whole system is "Shiro".
    // No setup?

    public static final Logger shiroConfigLog = LoggerFactory.getLogger(Fuseki.PATH+".Shiro");

    private static List<String> defaultIniFileLocations = List.of( "file:shiro.ini"
                                                                 , "file:run/shiro.ini"
                                                                 , "file:/etc/fuseki/shiro.ini");
    private static List<String> iniFileLocations = null;
    public static void setShiroIniLocations(List<String> shiroIniLocations) {
        iniFileLocations = shiroIniLocations;
    }

    /* In jena-fuseki-webapp (WAR file) and jena-fuseki-full-jar:
     *   FUSEKI_HOME - used to find "webapp"
     *   FUSEKI_BASE - used as the administration area
     *   jena-fuseki-webapp:
     *      WAR         (null, "/etc/fuseki") ,
     *      STANDALONE  (".", "run") ,
     */

    @Override
    public String name() { return "ModShiro"; }

    @Override public void prepare(FusekiServer.Builder serverBuilder, Set<String> datasetNames, Model configModel) {
        if ( ! Objects.equals("disabled", Lib.getenv("FUSEKI_SHIRO") ) ) {
            Filter filter = new FusekiShiroFilter();
            // This is a "before" filter.
            serverBuilder.addFilter("/*", filter);
        }
    }

    /**
     * FusekiShiroFilter, includes Shiro initialization.
     * Fuseki is a not a webapp e.g. not a WAR file
     * so it needs to trigger off servlet initialization.
     */
    private class FusekiShiroFilter extends ShiroFilter {
        boolean runnng = false;
        @Override public void init() throws Exception {
            // Can not initShiro in serverBeforeStarting()
            // and serverAfterStarting() is too late.
            initShiro(getServletContext());
            super.init();
        }
    }

    private static void initShiro(ServletContext servletContext) {
        List<String> locations = (iniFileLocations == null) ? defaultIniFileLocations : iniFileLocations;
        String fusekiBase = Lib.getenv("FUSEKI_BASE");
        if ( fusekiBase != null ) {
            Path pathShiroIni = Paths.get(fusekiBase).resolve("shiro.ini");
            if ( ! Files.exists(pathShiroIni) )
                throw new FusekiConfigException("No such file: $FUSEKI_BASE/shiro.ini ("+pathShiroIni+")");
            String resource = "file:"+pathShiroIni.toString();
            locations = List.of(resource);
        }
        FusekiShiroLib.shiroEnvironment(servletContext, locations);
    }

    @Override public void serverBeforeStarting(FusekiServer server) {
        // Shiro requires a session handler.
        // This needs the Jetty server to have been created.
        org.eclipse.jetty.server.Server jettyServer = server.getJettyServer();
        try {
            ServletContextHandler servletContextHandler = (ServletContextHandler)(jettyServer.getHandler());
            if ( servletContextHandler.getSessionHandler() == null ) {
                SessionHandler sessionsHandler = new SessionHandler();
                servletContextHandler.setHandler(sessionsHandler);
            }
        } catch (RuntimeException ex) {
            shiroConfigLog.error("Failed to set a session manager - server aborted");
            throw ex;
        }
    }

    @Override public void serverAfterStarting(FusekiServer server) {}

//    @Override public void serverStopped(FusekiServer server) { }
}
