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



package velosurf.cache;

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>Cache that keeps fetched instances in memory.</p>
 *
 * <p>Three modes (defined for &lt;<code>database</code>&gt; or for &lt;<code>entity</code>&gt; in <code>model.xml</code>:</p>
 * <ul>
 * <li>NO_CACHE (cache='none', the default) : no caching occurs on this entity.
 * <li>SOFT_CACHE (cache='soft') : caching occurs as long as memory is ont reclaimed (see the behaviour of java soft references).
 * <li>GROWING_CACHE (cache='growing') : caching occurs as long as instances are fetched or created.
 * <li>FULL_CACHE (cache='full') : the whole table is loaded into the cache at startup.
 * </ul>
 *
 * <p>For an entity's instances to be cached, the associated table must have a primary key (even if multivalued).</p>
 *
 * <p><b>Warning</b>: Velosurf will invalidate entries on single row update and delete queries, but global updates and deletes are not taken into account.</p>
 * <p>This caching mechanism is meant for straightforward optimizations in read-only or very simple situations, for instance to
 * avoid re-fetching data related to the logged user at each request.</p>
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 *
 */
public class Cache
{
    /**
     * Constant used to specify the "no cache" mode.
     */
    public static final int NO_CACHE = 0;

    /**
     * Constant used to specify the "soft cache" mode.
     */
    public static final int SOFT_CACHE = 1;

    /**
     * Constant used to specify the "full cache" mode.
     */
    public static final int FULL_CACHE = 2;

    /**
     * Constant used to specify the "growing cache" mode.
     */
    public static final int GROWING_CACHE = 3;

    /**
     * Cache constructor.
     *
     * @param cachingMethod required caching mode
     */
    public Cache(int cachingMethod)
    {
        this.cachingMethod = cachingMethod;
        innerCache = new HashMap();
    }

    /**
     * Put an instance in the cache.
     *
     * @param key key field(s) of this instance
     * @param value instance
     */
    public void put(String key, Object value)
    {
        value = (cachingMethod == SOFT_CACHE ? new SoftReference<Object>(value) : value);
        synchronized(innerCache)
        {
            innerCache.put(key, value);
        }
    }

    /**
     * Getter for the size of the cache.
     *
     * @return the size of the cache
     */
    public int size()
    {
        return innerCache.size();
    }

    /**
     * Try to get an instance from the cache.
     *
     * @param key key field(s) of the asked instance
     * @return Asked instance or null if not found
     */
    public Object get(String key)
    {
        Object ret;

        synchronized(innerCache)
        {
            ret = innerCache.get(key);
        }
        if(ret != null && cachingMethod == SOFT_CACHE)
        {
            ret = ((SoftReference<Object>)ret).get();

            /* if null, clean cache */
            if(ret == null)
            {
                synchronized(innerCache)
                {
                    innerCache.remove(key);
                }
            }
        }
        return ret;
    }

    /**
     * Clear the cache.
     *
     */
    public void clear()
    {
        innerCache.clear();
    }

    /**
     * invalidates an entry
     * (used after an insert or an update)
     */
    public void invalidate(String key)
    {
        synchronized(innerCache)
        {
            innerCache.remove(key);
        }
    }

    /**
     * The caching method this cache uses.
     */
    private int cachingMethod;

    /**
     * the inner map that stores associations.
     */
    private Map<String, Object> innerCache = null;

}
