/*
 * Copyright 2003 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package velosurf;

import velosurf.context.DBReference;
import velosurf.util.Logger;
import velosurf.util.UserContext;
import velosurf.util.XIncludeResolver;
import velosurf.sql.Database;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;

import org.apache.velocity.app.Velocity;

/** <p>This class is the Velosurf main entry class if you do not use the toolbox.xml mechanism.
 *  Unless you specify the config file to use, it will successively search for a configuration file :</p>
 *  <ul>
 *  <li><p>from the java property "config.velosurf" (like in 'java -Dconfig.velosurf=/.../velosurf.xml ...' )</p>
 *  <li><p>from the Velocity property "config.velosurf" (entry of the velocity.properties file)</p>
 *  <li><p>as './velosurf.xml', './conf/velosurf.xml', './WEB-INF/velosurf.xml', './cfg/velosurf.xml'
 * </ul>
 *
 * @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 *
 */


public class Velosurf extends DBReference
{
    /** Initialization flag.
     */
    private boolean initialized = false;

    /** Configuration file.
     */
    private String configFile = null;

    /** Empty constructor.
     */
    public Velosurf() {
    }

    /**
     * Constructor taking a File object as configuration.
     * @param configFile configuration file
     * @throws IOException
     * @throws SQLException
     */
    public Velosurf(File configFile) throws IOException,SQLException {
        this(new FileInputStream(configFile));
    }

    /**
     * Constructor taking an InputStream object as configuration.
     * @param config
     * @throws IOException
     * @throws SQLException
     */
    public Velosurf(InputStream config)  throws IOException,SQLException {
        initLogging();
        init(config);
    }

    /**
     * Explicitely set the configuration file.
     * @param config configuration pathname
     */
    public void setConfigFile(String config) {
        configFile = config;
    }

    /**
     * Initializes the logger.
     */
    private void initLogging() {
        Logger.log2Stderr();
    }

    /**
     * Tries to find a configuration file using some default locations.
     * @return the pathname of the configuration file, if found - null otherwise
     */
    private static String findConfig() {
        String pathname = null;
        File file = null;

        /* first, ask Java */
        pathname = System.getProperty("velosurf.config");
        if (pathname != null) {
            file = new File(pathname);
            if (file.exists()) return pathname;
        }

        /* then Velocity */
        pathname = (String)Velocity.getProperty("velosurf.config");
        if (pathname != null) {
            file = new File(pathname);
            if (file.exists()) return pathname;
        }

        /* then try some standard pathes */
        String [] guesses = {
            "./velosurf.xml","./conf/velosurf.xml","./WEB-INF/velosurf.xml","./cfg/velosurf.xml",
            "./model.xml","./conf/model.xml","./WEB-INF/model.xml","./cfg/model.xml"
        };
        for (int i=0;i<guesses.length;i++) {
            file = new File(guesses[i]);
            if (file.exists()) return guesses[i];
        }

        return null;
    }

    /**
     * Lazzy initialization.
     * @param config configuration file input stream
     * @throws SQLException
     * @throws IOException
     */
    private void init(InputStream config)  throws SQLException,IOException {
        Database db = Database.getInstance(config);
        super.init(db,new UserContext());
        initialized = true;
    }

    /**
     * Lazzy initialization.
     * @throws SQLException
     * @throws IOException
     */
    private void init() throws SQLException,IOException {
        if(configFile == null) {
            configFile = findConfig();
        }
        if(configFile == null) {
            throw new IOException("No Velosurf config file found. Please specify one using setConfig(pathname).");
        }
        /* calculate the base directory, for XInclude */
        /* Velosurf won't like '/' in windows names neither '\' in linux ones... Does Java? */
        String base = null;
        configFile.replace('\\','/');
        int i = configFile.lastIndexOf('/');
        if (i == -1) {
            base = ".";
        } else {
            base = configFile.substring(0,i);
        }
        Database db = Database.getInstance(new FileInputStream(configFile),new XIncludeResolver(base));
        super.init(db,new UserContext());
        initialized = true;
    }

    /**
     * Generic getter.
     * @param key
     * @return property
     */
    public Object get(Object key) {
        if (!initialized) {
            try {
                init();
            } catch(Exception e) {
                Logger.log(e);
                return null;
            }
        }
        return super.get(key);
    }

    /**
     * Generic setter.
     * @param key
     * @param value
     * @return old value
     */
    public Object put(Object key,Object value) {
        if (!initialized) {
            try {
                init();
            } catch(Exception e) {
                Logger.log(e);
                return null;
            }
        }
        return super.get(key);
    }

    /**
     * Schema name getter.
     * @return the name of the current schema
     */
    public String getSchema() {
        if (!initialized) {
            try {
                init();
            } catch(Exception e) {
                Logger.log(e);
                return null;
            }
        }
        return super.getSchema();
    }

    /** Obfuscate the given value.
     * @param value value to obfuscate
     * @return obfuscated value
     */
    public String obfuscate(Object value) {
        if (!initialized) {
            try {
                init();
            } catch(Exception e) {
                Logger.log(e);
                return null;
            }
        }
        return super.obfuscate(value);
    }

    /** De-obfuscate the given value.
     * @param value value to de-obfuscate
     * @return obfuscated value
     */
    public String deobfuscate(Object value) {
        if (!initialized) {
            try {
                init();
            } catch(Exception e) {
                Logger.log(e);
                return null;
            }
        }
        return super.deobfuscate(value);
    }

}
