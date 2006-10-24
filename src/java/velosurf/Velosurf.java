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

/** this class is the Velosurf main entry class if you do not use the toolbox.xml mechanism.
 *  Unless you specify the config file to use, it will successively search for a configuration file :
 *  <ul>
 *  <li><p>from the java property "config.velosurf" (like in 'java -Dconfig.velosurf=/.../velosurf.xml ...' )</p>
 *  <li><p>from the Velocity property "config.velosurf" (entry of the velocity.properties file)</p>
 *  <li><p>as './velosurf.xml', './conf/velosurf.xml', './WEB-INF/velosurf.xml', './cfg/velosurf.xml'
 *
 * @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 *
 */


public class Velosurf extends DBReference
{
    private boolean initialized = false;

    private String configFile = null;

    public Velosurf() throws IOException,SQLException
    {
//        this(findConfig());
    }

    public Velosurf(File file) throws IOException,SQLException
    {
        this(new FileInputStream(file));
    }

    public Velosurf(InputStream config)  throws IOException,SQLException
    {
        initLogging();
        init(config);
    }

    public void setConfigFile(String config) {
        configFile = config;
    }

    protected void initLogging()
    {
        Logger.log2Stderr();
    }

    protected static String findConfig() throws IOException {
        String pathname = null;
        File file = null;

        // first, ask Java
        pathname = System.getProperty("velosurf.config");
        if (pathname != null) {
            file = new File(pathname);
            if (file.exists()) return pathname;
        }

        // then Velocity
        pathname = (String)Velocity.getProperty("velosurf.config");
        if (pathname != null) {
            file = new File(pathname);
            if (file.exists()) return pathname;
        }

        // then try some standard pathes
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

    protected void init(InputStream config)  throws SQLException,IOException {
        Database db = Database.getInstance(config);
        super.init(db,new UserContext());
        initialized = true;
    }

    protected void init() throws SQLException,IOException {
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

    public Object get(Object inKey) {
        if (!initialized) {
            try {
                init();
            } catch(Exception e) {
                Logger.log(e);
                return null;
            }
        }
        return super.get(inKey);
    }

    public Object put(Object inKey,Object inValue) {
        if (!initialized) {
            try {
                init();
            } catch(Exception e) {
                Logger.log(e);
                return null;
            }
        }
        return super.get(inKey);
    }

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

    /** obfuscate the given value
     * @param value value to obfuscate
     *
     * @return obfuscated value
     */
    public String obfuscate(Object value)
    {
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

    /** de-obfuscate the given value
     * @param value value to de-obfuscate
     *
     * @return obfuscated value
     */
    public String deobfuscate(Object value)
    {
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
