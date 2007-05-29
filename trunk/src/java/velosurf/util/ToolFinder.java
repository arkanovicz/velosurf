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

package velosurf.util;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpSession;

/**
 * An utility class used to find a tool in the toolbox. For now, it is only implemented for session tools.
 *
 * @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */

public class ToolFinder  {

    private static String toolsMapKey = null;
	private static int toolsLibraryVersion = 0;

    static {
        try {
			Class.forName("org.apache.velocity.tools.view.VelocityView");
            // tools v2.x
            toolsLibraryVersion = 2;
        } catch(ClassNotFoundException cnfe) {
            // tools v1.x
            toolsLibraryVersion = 1;
            toolsMapKey = "org.apache.velocity.tools.view.servlet.ServletToolboxManager:session-tools";
        }
    }

    /**
     * Find a session tool.
     * @param session the http session the tool is
     * @return the tool if found in the session, or null
     */
    public static <T> T findSessionTool(HttpSession session, Class<T> toolClass) {
        if (session != null) {
            // check first in attributes if the tool registered itself
            Object registered = session.getAttribute(toolClass.getName());
            if(registered != null && toolClass.isAssignableFrom(registered.getClass())) {
                return (T)registered;
            }

            if(toolsLibraryVersion == 1) {
                // tools v1.x
                Map sessionTools = (Map)session.getAttribute(toolsMapKey);
                if (sessionTools != null) {
                    for(Object t:sessionTools.values()) {
                        if (toolClass.isAssignableFrom(t.getClass())) {
                            return (T)t;
                        }
                    }
                    Logger.warn("findtool: requested tool ("+toolClass.getName()+") not found!");
                } else {
                    Logger.warn("findtool: no tools map found in session!");
                }
            } else {
                // tools v2.x - TODO find a way...
                Logger.warn("findtool: no way to find requested tool: "+toolClass.getName());

/*                Object toolbox = session.getAttribute(toolsMapKey);
                if (toolbox != null) {
                    Map<String,Object> sessionTools;
                    try {
                        sessionTools = (Map<String,Object>)_getAll.invoke(toolbox,new Object[] {new HashMap()});
                    } catch(Exception e) {
                        Logger.error("findtool: error getting tool "+toolClass.getName());
                        Logger.log(e);
                        return null;
                    }
                    if (sessionTools != null) {
                        for(Object t:sessionTools.values()) {
                            if (toolClass.isAssignableFrom(t.getClass())) {
                                return (T)t;
                            }
                        }
                        Logger.warn("findtool: requested tool ("+toolClass.getName()+") not found!");
                    } else {
                        Logger.warn("findtool: could not retrieve the map of session tools!");
                    }
                } else {
                    Logger.warn("fintool: session toolbox not found!");
                }
*/
            }
        }
        return null;
    }
}
