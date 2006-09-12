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

import javax.servlet.http.HttpServletRequest;

import org.apache.velocity.tools.view.context.ViewContext;
import org.apache.velocity.tools.view.tools.ViewTool;

import velosurf.util.Logger;
import velosurf.model.Entity;

/** This class has about the same functionnalities as the tool org.apache.velocity.tools.view.tools.ParameterParser
 *  but it adds a generic setter, and the autofetching feature.
 *
 * It is meant for the query scope.
 *
 * @author Claude Brisson
 *
 **/
 public class HttpQueryTool extends HashMap implements ViewTool
{

    public HttpQueryTool() {
    }

    public void init(Object inViewContext) {

        if (!(inViewContext instanceof ViewContext)) {
            Logger.error("HttpQueryTool.init: can't initialize... bad scope ? (query scope expected)");
        }

        request = ((ViewContext)inViewContext).getRequest();

        // store query vars
        Enumeration params = request.getParameterNames();
        while (params.hasMoreElements()) {
            String param = (String)params.nextElement();
            String values[] = request.getParameterValues(param);
            if (values.length == 1) {
                put(param,values[0]);
                if (sAutofetchingEnabled && values[0].length()>0) {
                    AutoFetchInfos infos = (AutoFetchInfos)sAutofetchMap.get(param);
                    if (infos != null) {
                        if (infos.mProtect) put(infos.mName,infos.mEntity.fetch(values[0]));
                        else ((ViewContext)inViewContext).getVelocityContext().put(infos.mName,infos.mEntity.fetch(values[0]));
                    }
                }
            }
            else  put(param,Arrays.asList(values));
        }
    }

    public int getInt(String inKey) {
        String val = (String)get(inKey);
        return val == null ? 0 : Integer.parseInt(val);
    }

    public boolean getBoolean(String inKey) {
        String val = (String)get(inKey);
        return val == null ? false : Boolean.valueOf(val).booleanValue();
    }

	public String[] getStrings(String inKey) {
		String[] ret;
		Object value = get(inKey);
		if (value instanceof List) {
			List list = (List)value;
			ret = new String[list.size()];
			int i=0;
			for(Iterator it = list.iterator();it.hasNext();i++) {
				ret[i] = (String)it.next();
			}
		}
		else {
			ret = new String[1];
			ret[0] = (String)value;
		}
		return ret;
	}

	public int[] getInts(String inKey) {
		int[] ret;
		Object value = get(inKey);
		if (value instanceof List) {
			List list = (List)value;
			ret = new int[list.size()];
			int i=0;
			for(Iterator it = list.iterator();it.hasNext();i++) {
				ret[i] = Integer.parseInt((String)it.next());
			}
		}
		else {
			ret = new int[1];
			ret[0] = Integer.parseInt((String)value);
		}
		return ret;
	}

    public String getUri() {
    	return request.getRequestURI();
    }

    public static void autofetch(Entity entity,String param,String name,boolean protect) {
        sAutofetchingEnabled = true;
        sAutofetchMap.put(param,new AutoFetchInfos(entity,name,protect));
    }

    protected HttpServletRequest request = null;

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
    protected static boolean sAutofetchingEnabled = false;
    protected static Map sAutofetchMap = new HashMap();
}
