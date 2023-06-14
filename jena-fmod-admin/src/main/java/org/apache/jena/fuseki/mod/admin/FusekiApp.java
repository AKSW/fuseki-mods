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

package org.apache.jena.fuseki.mod.admin;

import static java.lang.String.format;
import static org.apache.jena.atlas.lib.Lib.getenv;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import org.apache.jena.atlas.RuntimeIOException;
import org.apache.jena.atlas.io.IOX;
import org.apache.jena.atlas.lib.FileOps;
import org.apache.jena.atlas.lib.InternalErrorException;
import org.apache.jena.atlas.lib.Lib;
import org.apache.jena.atlas.logging.FmtLog;
import org.apache.jena.cmd.CmdException;
import org.apache.jena.dboe.sys.Names;
import org.apache.jena.fuseki.Fuseki;
import org.apache.jena.fuseki.FusekiConfigException;
import org.apache.jena.fuseki.build.DatasetDescriptionMap;
import org.apache.jena.fuseki.build.FusekiConfig;
import org.apache.jena.fuseki.mgt.Template;
import org.apache.jena.fuseki.mgt.TemplateFunctions;
import org.apache.jena.fuseki.server.DataAccessPoint;
import org.apache.jena.fuseki.server.DataService;
import org.apache.jena.fuseki.server.FusekiVocab;
import org.apache.jena.fuseki.servlets.HttpAction;
import org.apache.jena.fuseki.servlets.ServletOps;
import org.apache.jena.rdf.model.*;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.jena.riot.RDFLanguages;
import org.apache.jena.sparql.core.DatasetGraph;
import org.apache.jena.sparql.core.assembler.AssemblerUtils;

public class FusekiApp {
    // Relative names of directories in the FUSEKI_BASE area.
    public static final String     databasesLocationBase    = "databases";
    // Place to put Lucene text and spatial indexes.
    //private static final String        databaseIndexesDir       = "indexes";

    public static final String     backupDirNameBase        = "backups";
    public static final String     configDirNameBase        = "configuration";
    public static final String     logsNameBase             = "logs";
    public static final String     systemFileAreaBase       = "system_files";
    public static final String     templatesNameBase        = "templates";
    public static final String     DFT_SHIRO_INI            = "shiro.ini";
    public static final String     DFT_CONFIG               = "config.ttl";

    /** Directory for TDB databases - this is known to the assembler templates */
    public static Path        dirDatabases       = null;

    /** Directory for writing backups */
    public static Path        dirBackups         = null;

    /** Directory for assembler files */
    public static Path        dirConfiguration   = null;

    /** Directory for assembler files */
    public static Path        dirLogs            = null;

//    /** Directory for system database */
//    public static Path        dirSystemDatabase  = null;

    /** Directory for files uploaded (e.g upload assembler descriptions); not data uploads. */
    public static Path        dirSystemFileArea  = null;

    /** Directory for assembler files */
    public static Path        dirTemplates       = null;

    private static boolean    initialized        = false;
    // Marks the end of successful initialization.
    /*package*/static boolean serverInitialized  = false;

//    /**
//     * Root of the Fuseki installation for fixed files.
//     * This may be null (e.g. running inside a web application container)
//     */
//    public static Path FUSEKI_HOME = null;

    /**
     * Root of the varying files in this deployment. Often $PWD/run.
     * This must be writable.
     */
    public static Path FUSEKI_BASE = null;

    // Default - "run" in the current directory.
    public static final String dftFusekiBase = "run";

    static void setEnvironment() {
        if ( FUSEKI_BASE == null ) {
            String x2 = getenv("FUSEKI_BASE");
            if ( x2 == null )
                x2 = dftFusekiBase;
            FUSEKI_BASE = Path.of(x2);
        }

        FmtLog.info(Fuseki.configLog, "FusekiEnv: FUSEKI_BASE = %s", FUSEKI_BASE);
        if ( Files.isRegularFile(FUSEKI_BASE) )
            throw new FusekiConfigException("FUSEKI_BASE exists but is a file");
        if ( ! Files.exists(FUSEKI_BASE) ) {
            try {
                Files.createDirectories(FUSEKI_BASE);
            } catch (IOException e) {
                throw new FusekiConfigException("Failed to create FUSEKI_BASE: "+FUSEKI_BASE);
            }
        }
        if ( ! Files.isWritable(FUSEKI_BASE) )
            throw new FusekiConfigException("FUSEKI_BASE exists but is not writable");

        FUSEKI_BASE = FUSEKI_BASE.toAbsolutePath();
    }

    static void setup() {
        // Command line arguments "--base" ...
        setEnvironment();
        FusekiApp.ensureBaseArea(FUSEKI_BASE);
    }

    /**
     * Create directories if found to be missing.
     */
    public static void ensureBaseArea(Path FUSEKI_BASE) {
        if ( Files.exists(FUSEKI_BASE) ) {
            if ( ! Files.isDirectory(FUSEKI_BASE) )
                throw new FusekiConfigException("FUSEKI_BASE is not a directory: "+FUSEKI_BASE);
            if ( ! Files.isWritable(FUSEKI_BASE) )
                throw new FusekiConfigException("FUSEKI_BASE is not writable: "+FUSEKI_BASE);
        } else {
            ensureDir(FUSEKI_BASE);
        }

        // Ensure FUSEKI_BASE has the assumed directories.
        dirTemplates        = writeableDirectory(FUSEKI_BASE, templatesNameBase);
        dirDatabases        = writeableDirectory(FUSEKI_BASE, databasesLocationBase);
        dirBackups          = writeableDirectory(FUSEKI_BASE, backupDirNameBase);
        dirConfiguration    = writeableDirectory(FUSEKI_BASE, configDirNameBase);
        dirLogs             = writeableDirectory(FUSEKI_BASE, logsNameBase);
        dirSystemFileArea   = writeableDirectory(FUSEKI_BASE, systemFileAreaBase);

        // ---- Initialize with files.

//        // Copy missing files into FUSEKI_BASE
        // Interacts with FMod_Shiro.
        if ( Lib.getenv("FUSEKI_SHIRO") == null ) {
            copyFileIfMissing(null, DFT_SHIRO_INI, FUSEKI_BASE);
            System.setProperty("FUSEKI_SHIRO", DFT_SHIRO_INI);
        }

        copyFileIfMissing(null, DFT_CONFIG, FUSEKI_BASE);
        for ( String n : Template.templateNames ) {
            copyFileIfMissing(null, n, FUSEKI_BASE);
        }

        serverInitialized = true;
    }

    /** Copy a file from src to dst under name fn.
     * If src is null, try as a classpath resource
     * @param src   Source directory, or null meaning use java resource.
     * @param fn    File name, a relative path.
     * @param dst   Destination directory.
     *
     */
    private static void copyFileIfMissing(Path src, String fn, Path dst) {
        Path dstFile = dst.resolve(fn);
        if ( Files.exists(dstFile) )
            return;

        // fn may be a path.
        if ( src != null ) {
            try {
                IOX.safeWrite(dstFile, output->Files.copy(src.resolve(fn), output));
            } catch (RuntimeIOException e) {
                throw new FusekiConfigException("Failed to copy file "+src.resolve(fn)+" to "+dstFile, e);
            }
        } else {
            copyFileFromResource(fn, dstFile);
        }
    }

    private static void copyFileFromResource(String fn, Path dstFile) {
        try {
            // Get from the file from area "org/apache/jena/fuseki/server"
            String absName = "org/apache/jena/fuseki/server/"+fn;
            InputStream input = FusekiApp.class
                    // Else prepends classname as path
                    .getClassLoader()
                    .getResourceAsStream(absName);

            if ( input == null )
                throw new FusekiConfigException("Failed to find resource '"+fn+"'");
            IOX.safeWrite(dstFile, (output)-> input.transferTo(output));
        }
        catch (RuntimeException e) {
            throw new FusekiConfigException("Failed to copy "+fn+" to "+dstFile, e);
        }
    }

    private static List<DataAccessPoint> processServerConfigFile(String configFilename) {
        if ( ! FileOps.exists(configFilename) ) {
            Fuseki.configLog.warn("Configuration file '" + configFilename+"' does not exist");
            return Collections.emptyList();
        }
        //Fuseki.configLog.info("Configuration file: " + configFilename);
        Model model = AssemblerUtils.readAssemblerFile(configFilename);
        if ( model.size() == 0 )
            return Collections.emptyList();
        List<DataAccessPoint> x = FusekiConfig.processServerConfiguration(model, Fuseki.getContext());
        return x;
    }

    private static DataAccessPoint configFromTemplate(String templateFile, String datasetPath,
                                                      boolean allowUpdate, Map<String, String> params) {
        DatasetDescriptionMap registry = new DatasetDescriptionMap();
        // ---- Setup
        if ( params == null ) {
            params = new HashMap<>();
            params.put(Template.NAME, datasetPath);
        } else {
            if ( ! params.containsKey(Template.NAME) ) {
                Fuseki.configLog.warn("No NAME found in template parameters (added)");
                params.put(Template.NAME, datasetPath);
            }
        }
        //-- Logging
        Fuseki.configLog.info("Template file: " + templateFile);
        String dir = params.get(Template.DIR);
        if ( dir != null ) {
            if ( ! Objects.equals(dir, Names.memName) && !FileOps.exists(dir) )
                throw new CmdException("Directory not found: " + dir);
        }
        //-- Logging

        datasetPath = DataAccessPoint.canonical(datasetPath);

        // DRY -- ActionDatasets (and others?)
        addGlobals(params);

        String str = TemplateFunctions.templateFile(templateFile, params, Lang.TTL);
        Lang lang = RDFLanguages.filenameToLang(str, Lang.TTL);
        StringReader sr =  new StringReader(str);
        Model model = ModelFactory.createDefaultModel();
        RDFDataMgr.read(model, sr, datasetPath, lang);

        // ---- DataAccessPoint
        Statement stmt = getOne(model, null, FusekiVocab.pServiceName, null);
        if ( stmt == null ) {
            StmtIterator sIter = model.listStatements(null, FusekiVocab.pServiceName, (RDFNode)null );
            if ( ! sIter.hasNext() )
                ServletOps.errorBadRequest("No name given in description of Fuseki service");
            sIter.next();
            if ( sIter.hasNext() )
                ServletOps.errorBadRequest("Multiple names given in description of Fuseki service");
            throw new InternalErrorException("Inconsistent: getOne didn't fail the second time");
        }
        Resource subject = stmt.getSubject();
        if ( ! allowUpdate ) {
            // Opportunity for more sophisticated "read-only" mode.
            //  1 - clean model, remove "fu:serviceUpdate", "fu:serviceUpload", "fu:serviceReadGraphStore", "fu:serviceReadWriteGraphStore"
            //  2 - set a flag on DataAccessPoint
        }
        DataAccessPoint dap = FusekiConfig.buildDataAccessPoint(subject, registry);
        return dap;
    }

    public static void addGlobals(Map<String, String> params) {
        if ( params == null ) {
            Fuseki.configLog.warn("FusekAppEnv.addGlobals : params is null", new Throwable());
            return;
        }

        if ( ! params.containsKey("FUSEKI_BASE") )
            params.put("FUSEKI_BASE", pathStringOrElse(FUSEKI_BASE, "unset"));
//        if ( ! params.containsKey("FUSEKI_HOME") )
//            params.put("FUSEKI_HOME", pathStringOrElse(FusekiAppEnv.FUSEKI_HOME, "unset"));
    }

    private static String pathStringOrElse(Path path, String dft) {
        if ( path == null )
            return dft;
        return path.toString();
    }

    // DRY -- ActionDatasets (and others?)
    private static Statement getOne(Model m, Resource s, Property p, RDFNode o) {
        StmtIterator iter = m.listStatements(s, p, o);
        if ( ! iter.hasNext() )
            return null;
        Statement stmt = iter.next();
        if ( iter.hasNext() )
            return null;
        return stmt;
    }

    private static DataAccessPoint datasetDefaultConfiguration( String name, DatasetGraph dsg, boolean allowUpdate) {
        name = DataAccessPoint.canonical(name);
        DataService ds = FusekiConfig.buildDataServiceStd(dsg, allowUpdate);
        DataAccessPoint dap = new DataAccessPoint(name, ds);
        return dap;
    }

    // ---- Helpers

    /** Ensure a directory exists, creating it if necessary.
     */
    private static void ensureDir(Path directory) {
        File dir = directory.toFile();
        if ( ! dir.exists() ) {
            boolean b = dir.mkdirs();
            if ( ! b )
                throw new FusekiConfigException("Failed to create directory: "+directory);
        }
        else if ( ! dir.isDirectory())
            throw new FusekiConfigException("Not a directory: "+directory);
    }

    private static void mustExist(Path directory) {
        File dir = directory.toFile();
        if ( ! dir.exists() )
            throw new FusekiConfigException("Does not exist: "+directory);
        if ( ! dir.isDirectory())
            throw new FusekiConfigException("Not a directory: "+directory);
    }

    private static boolean emptyDir(Path dir) {
        return dir.toFile().list().length <= 2;
    }

    private static boolean exists(Path directory) {
        File dir = directory.toFile();
        return dir.exists();
    }

    private static Path writeableDirectory(Path root , String relName ) {
        Path p = makePath(root, relName);
        ensureDir(p);
        if ( ! Files.isWritable(p) )
            throw new FusekiConfigException("Not writable: "+p);
        return p;
    }

    private static Path makePath(Path root , String relName ) {
        Path path = root.resolve(relName);
        // Must exist
//        try { path = path.toRealPath(); }
//        catch (IOException e) { IO.exception(e); }
        return path;
    }

    /**
     * Dataset set name to configuration file name. Return a configuration file name -
     * existing one or ".ttl" form if new
     */
    public static String datasetNameToConfigurationFile(HttpAction action, String dsName) {
        List<String> existing = existingConfigurationFile(dsName);
        if ( ! existing.isEmpty() ) {
            if ( existing.size() > 1 ) {
                action.log.warn(format("[%d] Multiple existing configuration files for %s : %s",
                                       action.id, dsName, existing));
                ServletOps.errorBadRequest("Multiple existing configuration files for "+dsName);
                return null;
            }
            return existing.get(0).toString();
        }

        return generateConfigurationFilename(dsName);
    }

    /** New configuration file name - absolute filename */
    public static String generateConfigurationFilename(String dsName) {
        String filename = dsName;
        // Without "/"
        if ( filename.startsWith("/"))
            filename = filename.substring(1);
        Path p = FusekiApp.dirConfiguration.resolve(filename+".ttl");
        return p.toString();
    }

    /** Return the filenames of all matching files in the configuration directory (absolute paths returned ). */
    public static List<String> existingConfigurationFile(String baseFilename) {
        try {
            List<String> paths = new ArrayList<>();
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(FusekiApp.dirConfiguration, baseFilename+".*") ) {
                stream.forEach((p)-> paths.add(FusekiApp.dirConfiguration.resolve(p).toString() ));
            }
            return paths;
        } catch (IOException ex) {
            throw new InternalErrorException("Failed to read configuration directory "+FusekiApp.dirConfiguration);
        }
    }

}
