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



package velosurf.web.auth;

import java.io.Serializable;
import java.util.Map;
import javax.servlet.ServletContext;

import org.apache.velocity.tools.view.ViewContext;
import velosurf.context.DBReference;
import velosurf.web.ContextListener;
import velosurf.web.VelosurfTool;

/**
 * <p>Authenticator basic implementation.</p>
 * <p>It accepts the four following parameters in <code>toolbox.xml</code>:</p>
 * <ul>
 * <li><code>method</code> (inherited from <code>BaseAuthenticator</code>) the encryption method to use (default to none,
 * an example client-side javascript encryption is provided for the method HmacMD5).</li>
 * <li><code>user-by-login</code> name of the Velosurf root attribute that returns a user given its login.</li>
 * <li><code>login-parameter</code> name of the external parameter 'login' in the previous attribute.</li>
 * <li><code>password-field</code> name of the password field.</li>
 * </ul>
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */
public class SimpleDBAuthenticator extends BaseAuthenticator implements Serializable
{
    /** database. */
    protected String configFile = null;
    protected transient DBReference db = null;

    /** key used in toolbox.xml to indicate the "user by login" root attribute. */
    private static final String USER_BY_LOGIN_KEY = "user-by-login";

    /** key used in toolbox.xml to indicate the name of the login parameter in the "user by login" attribute. */
    private static final String LOGIN_PARAMETER_KEY = "login-parameter";

    /** key used in toolbox.xml to indicate the name of the password field in the "user by login" attribute. */
    private static final String PASSWORD_FIELD_KEY = "password-field";

    /** default name of the "user by login" root attribute. */
    private static final String USER_BY_LOGIN_DEFAULT = "user_by_login";

    /** default name for the "login" parameter. */
    private static final String LOGIN_PARAMETER_DEFAULT = "login";

    /** default name of the "password" field. */
    private static final String PASSWORD_FIELD_DEFAULT = "password";

    /** "user by login" root attribute name. */
    private String userByLogin = USER_BY_LOGIN_DEFAULT;

    /** login parameter name */
    private String loginParameter = LOGIN_PARAMETER_DEFAULT;

    /** password field name */
    private String passwordField = PASSWORD_FIELD_DEFAULT;

// CB TODO - upgrade tool to 2.0 method
// this has only be done for 'config' parameter

    /**
     * key used in the toolbox (toolbox.xml) to set the name of the config file.
     */
    private static final String TOOLBOX_CONFIG_FILE_KEY = "config";

    /**
     * initialize this tool.
     * @param initData a view context
     */
    public void init(Object initData)
    {
        super.init(initData);
        
        // init only if there was no error in super class
        if(initData instanceof ViewContext)
        {
            if(db == null)
            {
                ServletContext ctx = ((ViewContext)initData).getServletContext();
                configFile = VelosurfTool.findConfigFile(ctx);
                initDB(ctx);
            }
        }
    }

    protected void initDB()
    {
        initDB(null);
    }

    protected void initDB(ServletContext ctx)
    {
        if (ctx == null)
        {
            ctx = ContextListener.getCurrentContext();
        }
        if (configFile != null)
        {
            db = VelosurfTool.getInstance(configFile, ctx);
        }
        else
        {
            db = VelosurfTool.getDefaultInstance(ctx);
        }
    }
	
    /**
     * externally set the db reference
     * @param db DBReference
     */
    public void setDBReference(DBReference db)
    {
        this.db = db;
    }

    /**
     * get the password for this login.
     * @param login login
     * @return password or null
     */
    public String getPassword(String login)
    {
        if (db == null)
        {
            initDB();
        }
        Map user = null;

        synchronized(db)
        {
            db.put(loginParameter, login);
            user = (Map)db.get(userByLogin);
        }
        if(user != null)
        {
            return(String)user.get(passwordField);
        }
        return null;
    }

    /**
     * get the user object for this login.
     * @param login login
     * @return user object
     */
    public Object getUser(String login)
    {
        if (db == null)
        {
            initDB();
        }
        synchronized(db)
        {
            db.put(loginParameter, login);
            return db.get(userByLogin);
        }
    }

    /**
     * configure this tool.
     * @param config
     */
    public void configure(Map config)
    {
        super.configure(config);
        if(config != null)
        {
            String value;

            value = (String)config.get(USER_BY_LOGIN_KEY);
            if(value != null)
            {
                userByLogin = value;
            }
            value = (String)config.get(PASSWORD_FIELD_KEY);
            if(value != null)
            {
                passwordField = value;
            }
            value = (String)config.get(LOGIN_PARAMETER_KEY);
            if(value != null)
            {
                loginParameter = value;
            }
            configFile = (String)config.get(TOOLBOX_CONFIG_FILE_KEY);
        }
    }
}
