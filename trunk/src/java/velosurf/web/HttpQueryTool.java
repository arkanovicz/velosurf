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
    /** extra values map. */
    private Map<String,Object> extraValues = new HashMap<String,Object>();

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

        /* review all parameter keys to interpret "dots" inside keynames (only one level for now - FIXME: implement a recursive behaviour) */
        for(Map.Entry<String,Object> entry:(Set<Map.Entry<String,Object>>)getSource().entrySet()) {
            String key = entry.getKey();
            int dot = key.indexOf('.');
            if (dot > 0 && dot < key.length()-1) {
                String parentKey = key.substring(0,dot);
                String subKey = key.substring(dot+1);
                Object value = entry.getValue();
                if (value.getClass().isArray() && ((String[])value).length == 1) {
                    value = ((String[])value)[0];
                }
                Map map = (Map)extraValues.get(parentKey);
                if (map == null) {
                    map = new HashMap();
                    extraValues.put(parentKey,map);
                }
                map.put(subKey,value);
            }
        }
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
     * Generic getter with String argument (necessary to properly overload super getter).
     * @param key key
     * @return value or null
     */
    public Object get(String key)
    {
        return get((Object)key);
    }

    /**
     * Generic setter.
     * @param key key
     * @param value value
     * @return previous value
     */
    public Object put(Object key, Object value) {
        return extraValues.put((String)key,value);
    }

    /**
     * Get the number of parameters.
     * @return number of parameters
     */
    public int size() {
        return getSource().size() + extraValues.size();
    }

    /**
     * Check for the presence of parameters.
     * @return true if empty
     */
    public boolean isEmpty() {
        return getSource().isEmpty() && extraValues.isEmpty();
    }

    /**
     * Check for the presence of a parameter.
     * @param key parameter name
     * @return true if present
     */
    public boolean containsKey(Object key) {
        return getSource().containsKey(key) || extraValues.containsKey(key);
    }

    /**
     * Check for the presence of a value.
     * @param value value to find
     * @return true if present
     */
    public boolean containsValue(Object value) {
        String[] array = new String[1];
        array[0] = (String)value;
        return getSource().containsValue(array) || extraValues.containsValue(value);
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
        Set ret = new HashSet();
        ret.addAll(getSource().keySet());
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
        Collection coll = getSource().values();
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
        Set<Entry> coll = getSource().entrySet();
        for(Entry entry:coll) {
            Object value = entry.getValue();
            if (value.getClass().isArray() && ((String[])value).length == 1) {
                value = ((String[])value)[0];
            }
            map.put(entry.getKey(),value);
        }
        Set<Entry> ret = new HashSet(map.entrySet());
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

	public Set<String> getExtraKeys() {
		return extraValues.keySet();
	}

    /**
     * Debugging method: returns a query string corresponding to query parameters
     * Warning: it also includes POST parameters (so strictly speaking 
     *  it's not the real query string)
     * @return reconstitued query string
     */
    public String getQueryString() {
        StringBuffer result = new StringBuffer();
        for(Map.Entry entry:entrySet()) {
            if(result.length() > 0) {
                result.append('&');
            }
            result.append(String.valueOf(entry.getKey()));
            result.append('=');
            result.append(String.valueOf(entry.getValue()));
        }
        return result.toString();
    }

    public Integer getInteger(String key)
    {
        Integer ret = super.getInteger(key);
        /* try in extraValues */
        if(ret == null) {
            Object v = extraValues.get(key);
            if (v != null)
            {
                try {
                    ret = Integer.parseInt(String.valueOf(v));
                } catch(NumberFormatException nfe) {}
            }
        }
        return ret;
    }

    /* TODO subclass other getXXX() methods */
}
