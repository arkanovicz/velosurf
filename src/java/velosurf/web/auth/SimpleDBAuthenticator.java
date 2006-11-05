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

import java.util.Map;

import org.apache.velocity.tools.view.context.ViewContext;

import velosurf.web.VelosurfTool;
import velosurf.web.auth.BaseAuthenticator;
import velosurf.context.DBReference;
import velosurf.context.Instance;
import velosurf.util.Logger;

/** Authenticator basic implementation.
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */

public class SimpleDBAuthenticator extends BaseAuthenticator {

    private DBReference db = null;

    private static final String USER_BY_LOGIN_KEY = "user-by-login";
    private static final String LOGIN_PARAMETER_KEY = "login-parameter";
    private static final String PASSWORD_FIELD_KEY = "password-field";

    private static final String USER_BY_LOGIN_DEFAULT = "user_by_login";
    private static final String LOGIN_PARAMETER_DEFAULT = "login";
    private static final String PASSWORD_FIELD_DEFAULT = "password";

    private Map config = null;

    private String userByLogin = USER_BY_LOGIN_DEFAULT;
    private String loginParameter = LOGIN_PARAMETER_DEFAULT;
    private String passwordField = PASSWORD_FIELD_DEFAULT;

    public void init(Object initData) {
        super.init(initData);

        // init only if there was no error in super class
        if (initData instanceof ViewContext) {
            if (db == null) {
                db = VelosurfTool.getDefaultInstance(((ViewContext)initData).getServletContext());
            }
        }

        if(config != null) {
            String value;
            value = (String)config.get(USER_BY_LOGIN_KEY);
            if (value != null) {
                userByLogin = value;
            }
            value = (String)config.get(PASSWORD_FIELD_KEY);
            if (value != null) {
                passwordField = value;
            }
            value = (String)config.get(LOGIN_PARAMETER_KEY);
            if (value != null) {
               loginParameter = value;
            }
        }
    }

    protected String getPassword(String login) {
        Instance user = null;
        synchronized(db) {
            db.put(loginParameter, login);
            user = (Instance)db.get(userByLogin);
        }
        if (user != null) {
            return (String)user.get(passwordField);
        }
        return null;
    }

    protected Object getUser(String login) {
        synchronized(db) {
            db.put(loginParameter, login);
            return db.get(userByLogin);
        }
    }

    public void configure(Map map) {
        super.configure(map);
        config = map;
    }
}
