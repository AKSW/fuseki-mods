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

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;

import javax.servlet.ServletContext;

import org.apache.jena.fuseki.FusekiConfigException;
import org.apache.jena.irix.IRIs;
import org.apache.shiro.io.ResourceUtils;
import org.apache.shiro.web.env.EnvironmentLoaderListener;

/*package*/ class FusekiShiroLib {
    private static final String FILE = "file";

    static void shiroEnvironment(ServletContext servletContext, List<String> possibleShiroIniFiles) {
        // Shiro environment initialization, done here because we don't have webapp listeners.
        EnvironmentLoaderListener shiroListener = new ShiroEnvironmentLoaderListener(possibleShiroIniFiles);
        try {
            shiroListener.initEnvironment(servletContext);
        } catch (org.apache.shiro.config.ConfigurationException ex) {
            ShiroEnvironmentLoaderListener.shiroConfigLog.error("Failed to initialize Shitro: "+ex.getMessage());
            throw new FusekiConfigException(ex.getMessage(), ex);
        }
    }

    /** Look for a Shiro ini file, returning the first found, or return null */
    static String huntForShiroIni(List<String> locations) {
        for ( String loc : locations ) {
            // If file:, look for that file.
            // If a relative name without scheme, look in FUSEKI_BASE, FUSEKI_HOME, webapp.
            String scheme = IRIs.scheme(loc);

            // Covers C:\\ as a "scheme name"
            if ( scheme != null ) {
                if ( scheme.equalsIgnoreCase(FILE)) {
                    // Test file: for exists
                    Path p = Path.of(loc.substring(FILE.length()+1));
                    if ( ! p.toFile().exists() )
                        continue;
                    // Fall through.
                }
                // Can't test - try
                return loc;
            }
            // No scheme .
            Path p = Path.of(loc);

            // try "run/" area.


//            String fn = resolve(FusekiEnv.FUSEKI_BASE, p);
//            if ( fn != null )
//                return "file://"+fn;
//            fn = resolve(FusekiEnv.FUSEKI_HOME, p);
//            if ( fn != null )
//                return "file://"+fn;

            // Try in webapp.

            try ( InputStream is = ResourceUtils.getInputStreamForPath(loc); ) {
                boolean exists = (is != null );
                return loc;
            } catch (IOException e) { }
        }
        return null;
    }
}
