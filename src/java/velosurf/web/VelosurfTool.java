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



package velosurf.web;

import java.io.InputStream;
import java.net.URL;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;
import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;
import org.apache.velocity.tools.view.context.ViewContext;
import velosurf.context.DBReference;
import velosurf.sql.Database;
import velosurf.util.Logger;
import velosurf.util.ServletLogWriter;
import velosurf.util.ToolFinder;
import velosurf.util.UserContext;
import velosurf.util.XIncludeResolver;
import velosurf.web.l10n.Localizer;

/**
 * <p>This class is a tool meant to be referenced in toolbox.xml (VelocityTools View 1.4) or tools.xml (VelocityTools View 2.0+)</p>
 * <p>It can be used in any scope you want (application/session/request), depending on the behaviour you need for the refinement and ordering mechanisms (which will follow the same scope).
 * The initialization itself is very fast once all static initialization has been done, so there is no performance bottleneck when using request scope.</p>
 * <p>You can have several instances of VelosurfTool, each with a distinct model configuration file.
 * This can be useful to have one instance per schema, or one instance per database if dealing with several databases.</p>
 * <p>For this to work, you have to use velocity-tools v1.3+, and give each instance the pathname of its configuration file
 * via the 'config' parameter in the toolbox.xml file, like this for velocity-tools prior to 2.0:</p>
 * <pre>
 *
 *  &lt;!-- first instance --&gt;
 *  &lt;tool&gt;
 *    &lt;key&gt;db1&lt;/key&gt;
 *    &lt;scope&gt;request&lt;/scope&gt;
 *    &lt;class&gt;velosurf.tools.VelosurfTool&lt;/scope&gt;
 *    &lt;parameter name="config" value="WEB-INF/db1.xml" /&gt;
 *  &lt;/tool&gt;
 *
 *  &lt;!-- second instance --&gt;
 *  &lt;tool&gt;
 *    &lt;key&gt;db2&lt;/key&gt;
 *    &lt;scope&gt;request&lt;/scope&gt;
 *    &lt;class&gt;velosurf.tools.VelosurfTool&lt;/scope&gt;
 *    &lt;parameter name="config" value="WEB-INF/db2.xml" /&gt;
 *   &lt;/tool&gt;
 *
 * </pre>
 * <p>And like this for velocity-tools v2.0+ :</p>
 * <pre>
 *
 *  &lt;toolbox scope="request"&gt;
 *    &lt;!-- first instance --&gt;
 *    &lt;tool key="db1" class="velosurf.tools.VelosurfTool" config="WEB-INF/db1.xml"/&gt;
 *    &lt;!-- second instance --&gt;
 *    &lt;tool key="db2" class="velosurf.tools.VelosurfTool" config="WEB-INF/db2.xml"/&gt;
 *  &lt;/toolbox&gt;
 * </pre>
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 *
 */
public class VelosurfTool extends DBReference
{
    /**
     * build a new VelosurfTool.
     */
    public VelosurfTool(){}

    /**
     * Initialize this instance using the given ViewContext.
     *
     * @param viewContext initialization data
     */
    public void init(Object viewContext) throws Exception
    {
        // getservlet context
        ViewContext viewctx = null;
        ServletContext ctx = null;

        if(viewContext instanceof ServletContext)
        {
            ctx = (ServletContext)viewContext;
        }
        else if(viewContext instanceof ViewContext)
        {
            viewctx = (ViewContext)viewContext;
            ctx = viewctx.getServletContext();
        }
        else
        {
            Logger.error("Error: Initialization: no valid initialization data found: got a "
                         + viewContext.getClass().getName());
            System.err.println("Error: Initialization: no valid initialization data found!");
        }
        if(!Logger.isInitialized() && ctx != null)
        {
            Logger.setWriter(new ServletLogWriter(ctx));
        }

        // get config file
        if(configFile == null)
        {    // if not already given by configure()
            configFile = findConfigFile(ctx);
        }

        UserContext userContext = null;

        /* user context */
        if(viewctx != null)
        {
            HttpSession session = viewctx.getRequest().getSession(false);

            if(session != null)
            {
                synchronized(session)
                {
                    userContext = (UserContext)session.getAttribute(UserContext.USER_CONTEXT_KEY);
                    if(userContext == null)
                    {
                        /* check in the database if already connected */
                        Database db = dbMap.get(configFile);

                        if(db != null)
                        {
                            userContext = db.getUserContext();
                        }
                        else
                        {
                            userContext = new UserContext();
                        }
                        session.setAttribute(UserContext.USER_CONTEXT_KEY, userContext);
                        if(fetchLocalizer)
                        {
                            Localizer localizer = ToolFinder.findSessionTool(session, Localizer.class);

                            if(localizer != null)
                            {
                                userContext.setLocalizer(localizer);
                            }
                            else
                            {
                                // don't search for it again
                                fetchLocalizer = false;
                                userContext.setLocale(viewctx.getRequest().getLocale());
                            }
                        }
                        else
                        {
                            userContext.setLocale(viewctx.getRequest().getLocale());
                        }
                    }
                }
                session.setAttribute(VelosurfTool.class.getName(), this);
            }
        }

        /* initialize with a new or existing connection */
        Database db = getConnection(configFile, ctx);

        if(db == null)
        {
            throw new SQLException("Could not connect to database.");
        }
        else
        {
            db.setUserContext(userContext);
        }
        super.init(db);
    }

    /**
     * Tries to find the configuration file.
     * @param ctx servelt context
     * @return found config file or null
     */
    static String findConfigFile(ServletContext ctx)
    {
        /* tries with a servlet context parameter */
        String configFile = ctx.getInitParameter(WEBAPP_CONFIG_FILE_KEY);

        if(configFile != null)
        {
//          Logger.warn("Use of the "+WEBAPP_CONFIG_FILE_KEY+" servlet context parameter is deprecated.");
//          Logger.warn("Consider moving this parameter to toolbox.xml.");
            return configFile;
        }
        configFile = ctx.getInitParameter(WEBAPP_CONFIG_FILE_KEY2);
        if(configFile != null)
        {
//          Logger.warn("Use of the "+WEBAPP_CONFIG_FILE_KEY+" servlet context parameter is deprecated.");
//          Logger.warn("Consider moving this parameter to toolbox.xml.");
            return configFile;
        }

        // else try default
        InputStream check = ctx.getResourceAsStream(DEFAULT_CONFIG_FILE);

        if(check == null)
        {
            check = ctx.getResourceAsStream(OLD_DEFAULT_CONFIG_FILE);
            if(check == null)
            {
                throw new RuntimeException(
                    "Velosurf config file not found! Please specify it using the servlet context or the toolbox parameters.");
            }
            else
            {
                configFile = OLD_DEFAULT_CONFIG_FILE;
            }
        }
        else
        {
            configFile = DEFAULT_CONFIG_FILE;
        }
        return configFile;
    }

    /**
     * initialization from a servlet context
     *
     */
    protected void initialize(ServletContext ctx){}

    /**
     * key used in the deployment descriptor (web.xml) to set the name of the config file.
     */
    private static final String WEBAPP_CONFIG_FILE_KEY = "velosurf.config";

    /**
     * alternate webapp key
     */
    private static final String WEBAPP_CONFIG_FILE_KEY2 = "velosurf.configuration";

    /**
     * key used in the toolbox (toolbox.xml) to set the name of the config file.
     */
    private static final String TOOLBOX_CONFIG_FILE_KEY = "config";

    /**
     * alternate key for the toolbox
     */
    private static final String TOOLBOX_CONFIG_FILE_KEY2 = "configuration";

    /**
     * default database config file.
     */
    private static final String DEFAULT_CONFIG_FILE = "/WEB-INF/model.xml";

    /**
     * old default database config file.
     */
    private static final String OLD_DEFAULT_CONFIG_FILE = "/WEB-INF/velosurf.xml";

    /**
     * path to the config file.
     */
    private String configFile = null;

    /**
     * database connections.
     */
    private static Map<String, Database> dbMap = new HashMap<String, Database>();

    /**
     * configure.
     *
     * @param map parameters
     */
    public void configure(Map<String, String> map)
    {
        configFile = map.get(TOOLBOX_CONFIG_FILE_KEY);
        if(configFile == null)
        {
            configFile = map.get(TOOLBOX_CONFIG_FILE_KEY2);
        }
    }

    /**
     * return the existing Database for the specified config file, or null
     * if it isn't already open.
     * @param configFile
     * @return a Database
     */
    protected static Database getConnection(String configFile)
    {
        if(!configFile.startsWith("/"))
        {
            configFile = "/" + configFile;
        }
        return dbMap.get(configFile);
    }

    /**
     * return a db reference on the existing Database for the specified config file, or null
     * if it isn't already open.
     * @param configFile
     * @return a DBReference
     */
    public static DBReference getInstance(String configFile)
    {
        if(!configFile.startsWith("/"))
        {
            configFile = "/" + configFile;
        }

        Database db = dbMap.get(configFile);

        return db == null ? null : new DBReference(db);
    }

    /**
     * return the existing Database for the specified config file and servlet context,
     * or null if an error occurs.
     * @param configFile
     * @return a Database
     */
    protected static Database getConnection(String configFile, ServletContext servletContext)
    {
        if(!configFile.startsWith("/"))
        {
            configFile = "/" + configFile;
        }

        Database db = (Database)dbMap.get(configFile);

        if(db == null)
        {
            try
            {
                Logger.info("Using config file '" + configFile + "'");

                InputStream is = servletContext.getResourceAsStream(configFile);

                if(is == null)
                {
                    Logger.error("Could not read config file " + configFile);
                    return null;
                }

                /* calculate the base directory, for XInclude */
                /* Velosurf won't like '/' in windows names or '\' in linux ones... Does Java anyway? */
                String base = null;

                configFile = configFile.replace('\\', '/');

                int i = configFile.lastIndexOf('/');

                if(i == -1)
                {
                    base = ".";
                }
                else
                {
                    base = configFile.substring(0, i);
                }
                db = Database.getInstance(is, new XIncludeResolver(base, servletContext));
                dbMap.put(configFile, db);
            }
            catch(Throwable e)
            {
                Logger.error("Could not get a connection!");
                Logger.log(e);
            }
        }
        return db;
    }

    /**
     * return a db reference on the existing Database for the specified config file and servlet context,
     * or null if an error occurs.
     * @param configFile
     * @return a DBReference
     */
    public static DBReference getInstance(String configFile, ServletContext servletContext)
    {
        Database db = getConnection(configFile, servletContext);

        return db == null ? null : new DBReference(db);
    }

    /**
     * return the existing Database for the default config file, or null
     * if it does not already exist.
     * @return a Database
     */
    protected static Database getDefaultConnection()
    {
        return(Database)dbMap.get(DEFAULT_CONFIG_FILE);
    }

    /**
     * return a db reference the existing Database for the default config file, or null
     * if it does not already exist.
     * @return a DBReference
     */
    public static DBReference getDefaultInstance()
    {
        Database db = getDefaultConnection();

        return db == null ? null : new DBReference(db);
    }

    /**
     * return the existing Database for the default config file and servlet context,
     * or null if an error occurs.
     * @return a Database
     */
    protected static Database getDefaultConnection(ServletContext servletContext)
    {
        return getConnection(DEFAULT_CONFIG_FILE, servletContext);
    }

    /**
     * return a db reference on the existing Database for the default config file
     * or null if an error occurs.
     * @return a Database
     */
    public static DBReference getDefaultInstance(ServletContext servletContext)
    {
        String configFile = findConfigFile(servletContext);

        if(configFile == null)
        {
            throw new RuntimeException(
                "VelosurfTool.getDefaultInstance: Configuration file not found! Please add a 'velosurf.config' servlet context parameter.");
        }

        Database db = getConnection(configFile, servletContext);

        return db == null ? null : new DBReference(db);
    }

    /**
     * do we need to try to fetch the localizer object?
     * True initially, false after one unsuccessful try.
     */
    private static boolean fetchLocalizer = true;
}
