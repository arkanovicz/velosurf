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
import java.util.Map;
import java.util.HashMap;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;

import org.apache.velocity.tools.view.context.ViewContext;

import velosurf.context.DBReference;
import velosurf.sql.Database;
import velosurf.util.Logger;
import velosurf.util.ServletLogWriter;
import velosurf.util.ToolFinder;
import velosurf.util.UserContext;
import velosurf.web.i18n.Localizer;
import velosurf.web.i18n.Localizer;

/** <p>This class is a tool meant to be referenced in toolbox.xml</p>
 * <p>It can be used in any scope you want (application/session/request), depending on the behaviour you need for the refinement and ordering mechanisms (which will follow the same scope).
 * The initialization itself is very fast once all static initialization has been done, so there is no performance bottleneck when using request scope.</p>
 *<p>Since version 1.0rc1, you can have several instances of VelosurfTool, each with a distinct configuration file.
 * This can be useful to have one instance per schema, or one instance per database if dealing with several databases.</p>
 * <p>For this to work, you have to uncomment 'Configurable' at the beginning of the class declaration in this file.
 * You can then give each instance the pathname of its configuration file via the 'config' parameter in the toolbox.xml file
 * (this is a brand new feature of VelocityTools 1.2, commented here because VelocityTools 1.2 has not been officially released yet,
 * you've got to grab it from the subversion head), like this :</p>
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
 *</pre>
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 */
public class VelosurfTool extends DBReference
{
    /** builds a new VelosurfTool
     */
    public VelosurfTool() {
    }

    /**
     * Initializes this instance using the given ViewContext
     *
     * @param inViewContext initialization data
     */
    public void init(Object inViewContext) {
        // get servlet context
        ViewContext viewctx = null;
        ServletContext ctx = null;
        if (inViewContext instanceof ServletContext) {
            ctx = (ServletContext)inViewContext;
        }
        else if (inViewContext instanceof ViewContext) {
            viewctx = (ViewContext)inViewContext;
            ctx = viewctx.getServletContext();
        }
        else {
            Logger.error("Initialization: no valid initialization data found!");
        }

        // get config file
        if (mConfigFile == null) { // if not already given by configure()
            // look in the servlet context parameters
            if (mConfigFile == null)
                mConfigFile = ctx.getInitParameter(DATABASE_CONFIG_FILE_KEY);

            // else try default
            if (mConfigFile == null) {
                mConfigFile = DEFAULT_DATABASE_CONFIG_FILE;
            }
        }

        if (!Logger.isInitialized() && ctx != null) {
            Logger.setWriter(new ServletLogWriter(ctx));
        }

        UserContext userContext = null;

        /* user context */
        if (viewctx != null) {
            HttpSession session = viewctx.getRequest().getSession(false);
            if (session != null) {
                synchronized(session) {
                    userContext = (UserContext)session.getAttribute(USER_CONTEXT_KEY);
                    if (userContext == null) {
                        userContext = new UserContext();
                        session.setAttribute(USER_CONTEXT_KEY,userContext);
                        if (sFetchLocalizer) {
                            Localizer localizer = ToolFinder.findTool(session,Localizer.class);
                            if (localizer != null) {
                                userContext.setLocalizer(localizer);
                            } else {
                                // don't search for it again
                                sFetchLocalizer = false;
                            }
                        }
                    }
                }
            }
        }

        /* initialize with a new or existing connection */
        super.init(getConnection(mConfigFile,ctx),userContext);

    }

    /** initialization
     *
     * @param inServletContext servlet context
     * @return database connection
     */
    protected Database initDB(ServletContext inServletContext) {
        try {
            Logger.info("Velosurf tool initialization...");

            return getConnection(mConfigFile);
        }
        catch (Exception e) {
            Logger.log(e);
            return null;
        }
    }

    /** key used to store the user context in the http session
     */
    protected static final String USER_CONTEXT_KEY = "velosurf.user.context";

    /** key used in the deployment descriptor (web.xml) to set the name of the config file
     */
    protected static final String DATABASE_CONFIG_FILE_KEY = "velosurf.config";

    /** default database config file
     */
    protected static final String DEFAULT_DATABASE_CONFIG_FILE = "/WEB-INF/model.xml";

    /** path to the config file
     */
    protected String mConfigFile = null;

    /** database connections
     */
    protected static Map<String,Database> sDBMap = new HashMap();

    /** configure
     *
     * @param map parameters
     */
    public void configure(Map map) {
        mConfigFile = (String)map.get("config");
    }

    /** returns the existing Database for the specified config file, or null
     * if it isn't already open.
     * @param inConfigFile
     * @return a Database
     */
    protected static Database getConnection(String inConfigFile) {
        if (!inConfigFile.startsWith("/")) inConfigFile = "/"+inConfigFile;
        return sDBMap.get(inConfigFile);
    }

    /** returns a db reference on the existing Database for the specified config file, or null
     * if it isn't already open.
     * @param inConfigFile
     * @return a DBReference
     */
    public static DBReference getInstance(String inConfigFile) {
        if (!inConfigFile.startsWith("/")) inConfigFile = "/"+inConfigFile;
        Database db = sDBMap.get(inConfigFile);
        return db == null ? null : new DBReference(db);
    }


    /** returns the existing Database for the specified config file and servlet context,
     * or null if an error occurs.
     * @param inConfigFile
     * @return a Database
     */
    protected static Database getConnection(String inConfigFile,ServletContext inServletContext) {
        if (!inConfigFile.startsWith("/")) {
            inConfigFile = "/"+inConfigFile;
        }
        Database db = (Database)sDBMap.get(inConfigFile);
        if (db == null) {
            try {
                Logger.debug("Using config file '"+inConfigFile+"'");
                InputStream is = inServletContext.getResourceAsStream(inConfigFile);
                if (is == null) {
                    return null;
                }
                db = Database.getInstance(is);
                sDBMap.put(inConfigFile,db);
            }
            catch(Exception e) {
                Logger.error("Could not get a connection!");
                Logger.log(e);
            }
        }
        return db;
    }

    /** returns a db reference on the existing Database for the specified config file and servlet context,
     * or null if an error occurs.
     * @param inConfigFile
     * @return a DBReference
     */
    public static DBReference getInstance(String inConfigFile,ServletContext inServletContext) {
        Database db = getConnection(inConfigFile,inServletContext);
        return db == null ? null : new DBReference(db);
    }


    /** returns the existing Database for the default config file, or null
     * if it does not already exist.
     * @return a Database
     */
    protected static Database getDefaultConnection()
    {
        return (Database)sDBMap.get(DEFAULT_DATABASE_CONFIG_FILE);
    }

    /** returns a db reference the existing Database for the default config file, or null
     * if it does not already exist.
     * @return a DBReference
     */
    public static DBReference getDefaultInstance() {
        Database db = getDefaultConnection();
        return db == null ? null : new DBReference(db);
    }

    /** returns the existing Database for the default config file and servlet context,
     * or null if an error occurs.
     * @return a Database
     */
    protected static Database getDefaultConnection(ServletContext inServletContext)
    {
        return getConnection(DEFAULT_DATABASE_CONFIG_FILE,inServletContext);
    }

    /** returns a db reference on the existing Database for the default config file and servlet context,
     * or null if an error occurs.
     * @return a Database
     */
    public static DBReference getDefaultInstance(ServletContext inServletContext)
    {
        /* check at least in the context params */
        String configFile  = inServletContext.getInitParameter(DATABASE_CONFIG_FILE_KEY);
        Database db = getConnection(configFile == null ? DEFAULT_DATABASE_CONFIG_FILE : configFile,inServletContext);
        return db == null ? null : new DBReference(db);
    }

    protected static boolean sLoggerInitialized = false;

    /**
     * do we need to try to fetch the localizer object ?
     * True initially, false after one unsuccessful try.
     */
    protected static boolean sFetchLocalizer = true;
}
