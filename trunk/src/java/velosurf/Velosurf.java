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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import org.apache.velocity.app.Velocity;
import velosurf.context.DBReference;
import velosurf.sql.Database;
import velosurf.util.Logger;
import velosurf.util.XIncludeResolver;

/**
 * <p>This class is the Velosurf main entry class if you do not use the toolbox.xml mechanism.
 *  Unless you specify the model file to use, it will successively search for a model file :</p>
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
    /**
     * Initialization flag.
     */
    private boolean initialized = false;

    /**
     * Configuration file.
     */
    private File configFile = null;

    /**
     * Empty constructor.
     */
    public Velosurf()
    {
        initLogging();
    }

    /**
     * Constructor taking a File object as model configuration file.
     * @param configFile model configuration file
     * @throws IOException
     * @throws SQLException
     */
    public Velosurf(String config) throws IOException, SQLException
    {
        initLogging();
        configFile = new File(config);
        init();
    }

    /**
     * Constructor taking a File object as model configuration file.
     * @param configFile model configuration file
     * @throws IOException
     * @throws SQLException
     */
    public Velosurf(File configFile) throws IOException, SQLException
    {
        initLogging();
        this.configFile = configFile;
        init();
    }

    /**
     * Constructor taking an InputStream object as model configuration.
     * @param config
     * @throws IOException
     * @throws SQLException
     * @deprecated use others constructor
     */
     public Velosurf(InputStream config) throws IOException, SQLException
    {
        initLogging();
        init(config);
    }

    /**
     * Explicitely set the model configuration file.
     * @param config model configuration pathname
     */
    public void setConfigFile(String config)
    {
        configFile = new File(config);
    }

    /**
     * Initializes the logger.
     */
    private void initLogging()
    {
        if(!Logger.isInitialized())
        {
            Logger.log2Stderr();
        }
    }

    /**
     * Tries to find a model configuration file using some default locations.
     * @return the pathname of the model configuration file, if found - null otherwise
     */
    private void findConfig()
    {
        String pathname = null;
        File file = null;

        /* first, ask Java */
        pathname = System.getProperty("velosurf.config");
        if(pathname != null)
        {
            file = new File(pathname);
            if(file.exists())
            {
                configFile = file;
            }
        }

        /* then Velocity */
        pathname = (String)Velocity.getProperty("velosurf.config");
        if(pathname != null)
        {
            file = new File(pathname);
            if(file.exists())
            {
                configFile = file;
            }
        }

        /* then try some standard pathes */
        String[] guesses =
        {
            "./velosurf.xml", "./conf/velosurf.xml", "./WEB-INF/velosurf.xml", "./cfg/velosurf.xml", "./model.xml",
            "./conf/model.xml", "./WEB-INF/model.xml", "./cfg/model.xml"
        };

        for(int i = 0; i < guesses.length; i++)
        {
            file = new File(guesses[i]);
            if(file.exists())
            {
                configFile = file;
                break;
            }
        }
    }

    /**
     * Lazzy initialization.
     * @param config model configuration file input stream
     * @throws SQLException
     * @throws IOException
     */
    private void init(InputStream config) throws SQLException, IOException
    {
        Database db = Database.getInstance(config);

        super.init(db);
        initialized = true;
    }

    /**
     * Lazzy initialization.
     * @throws SQLException
     * @throws IOException
     */
    private void init() throws SQLException, IOException
    {
        if(configFile == null)
        {
            findConfig();
        }
        if(configFile == null)
        {
            throw new IOException("No Velosurf config file found. Please specify one using setConfig(pathname).");
        }

        Database db = Database.getInstance(new FileInputStream(configFile), new XIncludeResolver(configFile.getParent()));

        super.init(db);
        initialized = true;
    }

    /**
     * Allows to access the underlying velosurf.sql.Database object
     * @return a Database object
     */
    public Database getDatabase()
    {
        return db;
    }

    /**
     * Generic getter.
     * @param key
     * @return property
     */
    public Object get(Object key)
    {
        if(!initialized)
        {
            try
            {
                init();
            }
            catch(Exception e)
            {
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
    public Object put(String key, Object value)
    {
        if(!initialized)
        {
            try
            {
                init();
            }
            catch(Exception e)
            {
                Logger.log(e);
                return null;
            }
        }
        return super.put(key, value);
    }

    /**
     * Schema name getter.
     * @return the name of the current schema
     */
    public String getSchema()
    {
        if(!initialized)
        {
            try
            {
                init();
            }
            catch(Exception e)
            {
                Logger.log(e);
                return null;
            }
        }
        return super.getSchema();
    }

    /**
     * Obfuscate the given value.
     * @param value value to obfuscate
     * @return obfuscated value
     */
    public String obfuscate(Object value)
    {
        if(!initialized)
        {
            try
            {
                init();
            }
            catch(Exception e)
            {
                Logger.log(e);
                return null;
            }
        }
        return super.obfuscate(value);
    }

    /**
     * De-obfuscate the given value.
     * @param value value to de-obfuscate
     * @return obfuscated value
     */
    public String deobfuscate(Object value)
    {
        if(!initialized)
        {
            try
            {
                init();
            }
            catch(Exception e)
            {
                Logger.log(e);
                return null;
            }
        }
        return super.deobfuscate(value);
    }
}
