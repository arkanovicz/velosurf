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



package velosurf.sql;

import java.io.Serializable;
import java.sql.SQLException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import velosurf.util.Logger;
import velosurf.util.SlotMap;

/**
 * A wrapper implementing the Map interface for some objets having only getters.
 * Only <code>get(key)</code> and <code>keySet()</code> are implemented.
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 */
public class ReadOnlyMap implements SlotMap
{
    /**
     * Constructor.
     * @param source the wrapped row handler
     */
    public ReadOnlyMap(RowHandler source)
    {
        this.source = source;
    }

    /**
     * Not implemented.
     * @return 0
     */
    public int size()
    {
        return 0;
    }

    /**
     * Not implemented.
     * @return false
     */
    public boolean isEmpty()
    {
        return false;
    }

    /**
     * Not implemented.
     * @param key
     * @return false
     */
    public boolean containsKey(Object key)
    {
        return false;
    }

    /**
     * Not implemented.
     * @param value
     * @return false
     */
    public boolean containsValue(Object value)
    {
        return false;
    }

    /**
     * Get a value by key.
     * @param key value key
     * @return value
     */
    public Serializable get(Object key)
    {
        try
        {
            return source.get((String)key);
        }
        catch(SQLException sqle)
        {
            Logger.error("Could not retrieve value of key " + key + " on a " + source.getClass().getName());
            Logger.log(sqle);
            return null;
        }
    }

    /**
     * Not implemented.
     * @param query
     * @param key
     * @return null
     */
    public Serializable put(String key, Serializable value)
    {
        return null;
    }

    /**
     * Not implemented.
     * @param key
     * @return null
     */
    public Serializable remove(Object key)
    {
        return null;
    }

    /**
     * Not implemented.
     * @param map
     */
    public void putAll(Map<? extends String, ? extends Serializable> map){}

    /**
     * Not implemented.
     */
    public void clear(){}

    /**
     * Returns the set of keys.
     * @return the set of keys
     */
    public Set<String> keySet()
    {
        try
        {
            return source.keySet();
        }
        catch(SQLException sqle)
        {
            Logger.error("Could not retrieve keyset of a " + source.getClass().getName());
            Logger.log(sqle);
            return null;
        }
    }

    /**
     * Not implemented.
     * @return null
     */
    public Collection<Serializable> values()
    {
        return null;
    }

    /**
     * Not implemented.
     * @return null
     */
    public Set<Entry<String, Serializable>> entrySet()
    {
        return null;
    }

    private RowHandler source;
}
