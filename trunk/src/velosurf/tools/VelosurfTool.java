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

package velosurf.tools;

import java.io.InputStream;
import java.util.Map;
import java.util.HashMap;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpSession;

import org.apache.velocity.tools.view.context.ViewContext;
import org.apache.velocity.tools.view.tools.ViewTool;
import org.apache.velocity.tools.view.tools.Configurable;

import velosurf.context.DBReference;
import velosurf.sql.Database;
import velosurf.util.Logger;
import velosurf.util.ServletLogWriter;

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
 * @author Claude Brisson
 *
 */
public class VelosurfTool extends DBReference implements ViewTool /*,Configurable*/
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
        ServletContext ctx = null;
        boolean hasViewContext;
        if (inViewContext instanceof ServletContext) {
            ctx = (ServletContext)inViewContext;
            hasViewContext = false;
        }
        else if (inViewContext instanceof ViewContext) {
            ctx = ((ViewContext)inViewContext).getServletContext();
            hasViewContext = true;
        }
        else {
            Logger.error("Initialization: no valid initialization data found!");
            hasViewContext = false;
        }

        // get config file
        if (mConfigFile == null) { // if not already given by configure()
            // look in the servlet parameters
            if (mConfigFile == null)
                mConfigFile = ctx.getInitParameter(DATABASE_CONFIG_FILE_KEY);

            // else try default
            if (mConfigFile == null) {
                mConfigFile = DEFAULT_DATABASE_CONFIG_FILE;
            }
        }

        Database db;
        synchronized(sDBMap) {
            db = getConnection(mConfigFile);
            if (db == null) db = initDB(ctx);
        }

        super.init(db);

        if (sFetchLocalizer && hasViewContext) fetchLocalizer((ViewContext)inViewContext);
    }

	/** initialization
	 *
	 * @param inServletContext servlet context
	 * @return database connection
	 */
    protected Database initDB(ServletContext inServletContext) {
        try {
            // init log
            Logger.setWriter(new ServletLogWriter(inServletContext));
            Logger.info("Velosurf tool initialization...");

            return getConnection(mConfigFile);
        }
        catch (Exception e) {
            Logger.log(e);
            return null;
        }
    }

	/** key used in the deployment descriptor (web.xml) to set the name of the config file
	 */
    protected static final String DATABASE_CONFIG_FILE_KEY = "velosurf.config";

    /** default database config file
     */
    protected static final String DEFAULT_DATABASE_CONFIG_FILE = "/WEB-INF/velosurf.xml";

    /** path to the config file
     */
    protected String mConfigFile = null;

	/** database connections
	 */
    protected static Map sDBMap = new HashMap();

    /** configure
     *
     * @param map parameters
     */
    public void configure(Map map) {
        mConfigFile = (String)map.get("config");
        String localizerToolKey = (String)map.get("localizer");
        mLocalizerToolKey = (localizerToolKey == null ? sDefaultLocalizerToolKey : localizerToolKey);
    }

    /** returns the existing Database for the specified config file, or null
     * if it does not already exist.
     * @param inConfigFile
     * @return a Database
     */

    public static Database getConnection(String inConfigFile) {
        if (!inConfigFile.startsWith("/")) inConfigFile = "/"+inConfigFile;
        return (Database)sDBMap.get(inConfigFile);
    }

    /** returns the existing Database for the specified config file and servlet context,
     * or null if an error occurs.
     * @param inConfigFile
     * @return a Database
     */

    public static Database getConnection(String inConfigFile,ServletContext inServletContext) {
        if (!inConfigFile.startsWith("/")) inConfigFile = "/"+inConfigFile;
        Database db = (Database)sDBMap.get(inConfigFile);
        if (db == null) {
            try {
                Logger.debug("Using config file '"+inConfigFile+"'");
                InputStream is = inServletContext.getResourceAsStream(inConfigFile);
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


    /** returns the existing Database for the default config file, or null
     * if it does not already exist.
     * @return a Database
     */
    public static Database getDefaultConnection()
    {
        return (Database)sDBMap.get(DEFAULT_DATABASE_CONFIG_FILE);
    }

    /** returns the existing Database for the default config file and servlet context,
     * or null if an error occurs.
     * @return a Database
     */
    public static Database getDefaultConnection(ServletContext inServletContext)
    {
        return getConnection(DEFAULT_DATABASE_CONFIG_FILE,inServletContext);
    }

    /** default localizer tool key
     */

    protected static final String sDefaultLocalizerToolKey = "local";

    /**
     * key of the localizer tool in toolbox.xml
     */
    protected String mLocalizerToolKey = null;

    /**
     * do we need to try to fetch the localizer object ?
     * True initially, false after one unsuccessful try.
     */

    protected static boolean sFetchLocalizer = true;

    protected void fetchLocalizer(ViewContext inViewContext) {
        if (mLocalizer == null) {
            HttpSession session = inViewContext.getRequest().getSession(false);
            if (session != null) {
                Map sessionTools = (Map)session.getAttribute("org.apache.velocity.tools.view.servlet.ServletToolboxManager:session-tools");
                if (sessionTools != null) {
                    mLocalizer = (Localizer)sessionTools.get(mLocalizerToolKey);
                }
            }
            if (mLocalizer == null) {
                // don't search for it again
                sFetchLocalizer = false;
            }
        }
    }
}
