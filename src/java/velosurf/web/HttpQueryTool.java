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

import org.apache.velocity.tools.view.context.ViewContext;
import org.apache.velocity.tools.view.tools.ParameterParser;

import velosurf.util.Logger;

/** This class extends the tool org.apache.velocity.tools.view.tools.ParameterParser,
 *  adding a generic setter. Values that are set manually hide any previous values that
 *  are present in the query under the same key.
 *
 * It is meant for the query scope.
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 *
 **/
 public class HttpQueryTool extends ParameterParser implements Map
{
    /** view context. */
    private ViewContext context = null;
    /** extra values map. */
    private Map extraValues = new HashMap();

    /**
     * Constructor.
     */
    public HttpQueryTool() {
    }

    /**
     * Initialize this tool.
     * @param viewContext view context
     */
    public void init(Object viewContext) {

        super.init(viewContext);

        if (!(viewContext instanceof ViewContext)) {
            Logger.error("HttpQueryTool.init: can't initialize... bad scope ? (query scope expected)");
            throw new IllegalArgumentException("expecting a ViewContext argument");
        }
        context = (ViewContext)viewContext;
    }

    /**
     * Generic getter.
     * @param key key
     * @return value or null
     */
    public Object get(Object key)
    {
        Object ret = extraValues.get(key);
        if (ret == null) {
            return super.get(key.toString());
        } else {
            return ret;
        }
    }

    /**
     * Generic setter.
     * @param key key
     * @param value value
     * @return previous value
     */
    public Object put(Object key, Object value) {
        return extraValues.put(key,value);
    }

    /**
     * Get the number of parameters.
     * @return number of parameters
     */
    public int size() {
        return context.getRequest().getParameterMap().size() + extraValues.size();
    }

    /**
     * Check for the presence of parameters.
     * @return true if empty
     */
    public boolean isEmpty() {
        return context.getRequest().getParameterMap().isEmpty() && extraValues.isEmpty();
    }

    /**
     * Check for the presence of a parameter.
     * @param key parameter name
     * @return true if present
     */
    public boolean containsKey(Object key) {
        return context.getRequest().getParameterMap().containsKey(key) || extraValues.containsKey(key);
    }

    /**
     * Check for the presence of a value.
     * @param value value to find
     * @return true if present
     */
    public boolean containsValue(Object value) {
        String[] array = new String[1];
        array[0] = (String)value;
        return context.getRequest().getParameterMap().containsValue(array) || extraValues.containsValue(value);
    }

    /**
     * Remove a parameter (from extra values).
     * @param key parameter name
     * @return value or null
     */
    public Object remove(Object key) {
        return extraValues.remove(key);
    }

    /**
     * Put all key/values from a map.
     * @param map source map
     */
    public void putAll(Map map) {
        extraValues.putAll(map);
    }

    /**
     * Clear extra parameters.
     */
    public void clear() {
        extraValues.clear();
    }

    /**
     * Get the set of parameter names.
     * @return set of names
     */
    public Set keySet() {
        Set ret = context.getRequest().getParameterMap().keySet();
        ret.addAll(extraValues.keySet());
        return ret;
    }

    /**
     * Get the collection of values
     * @return collection of values
     */
    public Collection values() {
        String[] array;
        Collection ret = new HashSet();
        Collection coll = context.getRequest().getParameterMap().values();
        for(Object value:coll) {
            if(value.getClass().isArray()) {
                array=(String[])value;
                ret.add( array.length==1 ?  (Object)array[0] : array);
            } else {
                ret.add(value);
            }
        }
        ret.addAll(extraValues.values());
        return ret;
    }

    public Set<Entry> entrySet() {
        Map map = new HashMap();
        Set<Entry> coll = context.getRequest().getParameterMap().entrySet();
        for(Entry entry:coll) {
            Object value = entry.getValue();
            if (value.getClass().isArray() && ((String[])value).length == 1) {
                value = ((String[])value)[0];
            }
            map.put(entry.getKey(),value);
        }
        Set<Entry> ret = map.entrySet();
        ret.addAll(extraValues.entrySet());
        return ret;
    }

    public String toString() {
        StringBuilder ret = new StringBuilder("{ ");
        for(Entry entry:entrySet()) {
            ret.append((String)entry.getKey());
            ret.append('=');
            Object value =
            ret.append(entry.getValue().toString());
            ret.append(' ');
        }
        ret.append('}');
        return ret.toString();
    }
}
