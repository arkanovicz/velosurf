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

import javax.servlet.http.HttpSession;
import java.util.Map;

/**
 * An utility class used to find a tool in the toolbox. For now, it is only implemented for session tools.
 *
 * @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */

public class ToolFinder  {

    private static final String toolsMapKey = "org.apache.velocity.tools.view.servlet.ServletToolboxManager:session-tools";

    /**
     *
     * @param session the http session the tool is
     * @return the tool if found in the session, or null
     */
    public static <T> T findTool(HttpSession session, Class<T> toolClass) {
        if (session != null) {
            Map sessionTools = (Map)session.getAttribute(toolsMapKey);
            if (sessionTools != null) {
                for(Object t:sessionTools.values()) {
                    if (toolClass.isAssignableFrom(t.getClass())) {
                        return (T)t;
                    }
                }
            } else {
                Logger.warn("findtool: no tools map found in session!");
            }
        }
        return null;
    }
}
