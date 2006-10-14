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

import java.util.*;
import java.sql.SQLException;

import javax.servlet.http.HttpServletRequest;

import org.apache.velocity.tools.view.context.ViewContext;
import org.apache.velocity.tools.view.tools.ParameterParser;

import velosurf.util.Logger;
import velosurf.model.Entity;

/** This class extends the tool org.apache.velocity.tools.view.tools.ParameterParser,
 *  adding a generic setter, and the autofetching feature.
 *
 * It is meant for the query scope.
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 **/
 public class HttpQueryTool extends ParameterParser
{

    private ViewContext context = null;
    private Map extraValues = new HashMap();

    public HttpQueryTool() {
    }

    public void init(Object inViewContext) {

        super.init(inViewContext);

        if (!(inViewContext instanceof ViewContext)) {
            Logger.error("HttpQueryTool.init: can't initialize... bad scope ? (query scope expected)");
            throw new IllegalArgumentException("expecting a ViewContext argument");
        }

        context = (ViewContext)inViewContext;
        HttpServletRequest request = context.getRequest();

        if(sAutofetchingEnabled) {
            autofetch(request.getParameterMap(),context);
        }
    }

    private void autofetch(Map parameters,ViewContext context) {
        for(Map.Entry param:(Set<Map.Entry>)parameters.entrySet()) {
            String[] values = (String[])param.getValue();
            if (values.length == 1) {
                String value = values[0];
                AutoFetchInfos infos = (AutoFetchInfos)sAutofetchMap.get(param.getKey());
                if (infos != null) {
                    try {
                        if (infos.mProtect) put(infos.mName,infos.mEntity.fetch(value));
                        else context.getVelocityContext().put(infos.mName,infos.mEntity.fetch(value));
                    } catch(SQLException sqle) {
                        Logger.error("autofetch failed!");
                        Logger.log(sqle);
                    }
                }
            }
        }
    }

    public Object get(String key)
    {
        Object ret = extraValues.get(key);
        if (ret == null) {
            return super.get(key);
        } else {
            return ret;
        }
    }

    public Object put(String key, Object value) {
        return extraValues.put(key,value);
    }

    public static void addAutofetch(Entity entity,String param,String name,boolean protect) {
        sAutofetchingEnabled = true;
        sAutofetchMap.put(param,new AutoFetchInfos(entity,name,protect));
    }

    protected static class AutoFetchInfos {
        public AutoFetchInfos(Entity entity,String name,boolean protect) {
            mEntity = entity;
            mName = name;
            mProtect = protect;
        }
        Entity mEntity;
        String mName;
        boolean mProtect;
    }

    private static boolean sAutofetchingEnabled = false;
    private static Map sAutofetchMap = new HashMap();
}
