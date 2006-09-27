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

// FIXME : should check somewhere that tables with no key cannot be cached

import java.lang.ref.SoftReference;
import java.util.HashMap;
import java.util.Map;

/** Cache that keeps fetched instances in memory.
 *
 * <p>
 * <p>
 * Three modes :
 * <ul>
 * <li>NO_CACHE
 * (cache='none', the default, in velosurf.xml) : no caching occurs on this entity.
 * <li>SOFT_CACHE (cache='soft' in velosurf.xml) : caching occurs as long as memory is ont reclaimed (see the behaviour of java soft references).
 * <li>FULL_CACHE (cache='full' in velosuf.xml) : caching occurs until manually freed.
 * </ul>
 *
 * For an entity's instances to be cached, the associated table must have a primary key (even if multivalued).
 * <p>
 * This caching mechanism is meant for straightforward optimizations in simple situations, for instance to
 * avoid re-fetching the loggued user at each request. Please keep in mind that the cache can quickly
 * become inconsistant if used in conjunction with complex modification queries (that occur in its back...).
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 */
public class Cache {

    /** constant used to specify the "no cache" mode
     */
    public static final int NO_CACHE = 0;
    /** constant used to specify the "soft cache" mode
     */
    public static final int SOFT_CACHE = 1;
    /** constant used to specify the "full cache" mode
     */
    public static final int FULL_CACHE = 2; // FIXME : need user cleaning methods

    /** Cache constructor
     *
     * @param inCachingMethod required caching mode
     */
    public Cache(int inCachingMethod) {
        mCachingMethod = inCachingMethod;
        mInnerCache = new HashMap();
    }

    /** Put an instance in the cache
     *
     * @param inKey key field(s) of this instance
     * @param inValue instance
     */
    public void put(Object inKey,Object inValue) {
        Object key = (inKey.getClass().isArray()?new ArrayKey((Object[])inKey):inKey);
        Object value = (mCachingMethod == SOFT_CACHE?new SoftReference(inValue):inValue);
        synchronized(mInnerCache) {
            mInnerCache.put(key,value);
        }
    }

    /** Getter for the size of the cache
     *
     * @return the size of the cache
     */
    public int size() {
        return mInnerCache.size();
    }

    /** Try to get an instance from the cache
     *
     * @param inKey key field(s) of the asked instance
     * @return Asked instance or null if not found
     */
    public Object get(Object inKey) {
        Object key = (inKey.getClass().isArray()?new ArrayKey((Object[])inKey):inKey);
        Object ret;
        synchronized(mInnerCache) {
            ret = mInnerCache.get(key);
        }
        if (ret != null && mCachingMethod == SOFT_CACHE) {
            ret = ((SoftReference)ret).get();
            // if null, clean cache
            if (ret == null) {
                synchronized(mInnerCache) {
                    mInnerCache.remove(key);
                }
            }
        }
        return ret;
    }

    /** Clear the cache
    *
    */
    public void clear() {
        mInnerCache.clear();
    }

    /** The caching method this cache uses
     */
    protected int mCachingMethod;
    /** the inner map that stores associations
     */
    protected Map mInnerCache = null;

    public static final class ArrayKey {

        /** ArrayKey is a simple wrapper that provides a field-to-field equal method between encapsulated arrays
         *
         * @param inKeys key values
         */
        public ArrayKey(Object[] inKeys) {
            mKeys = inKeys;
        }

        /** checks the cell-to-cell equality of two arrays
         *
         * @param inSource source array
         * @return a boolean indicating the equality
         */
        public boolean equals(Object inSource) {
            if (inSource instanceof ArrayKey) {
                ArrayKey k = (ArrayKey)inSource;
                if (k.mKeys.length == mKeys.length) {
                    for(int i=0;i<mKeys.length;i++)
                        if (! mKeys[i].equals(k.mKeys[i])) return false;
                    return true;
                }
            }
            return false;
        }

        /** hashcode of an array, based on the hashcode of its members
         *
         * @return the hashcode
         */
        public int hashCode() {
            int hash = 0;
            for (int i=0;i<mKeys.length;i++)
                hash += mKeys[i].hashCode();
            return hash;
        }

        /** the wrapped array
         */
        protected Object[] mKeys = null;
    } // end of inner class ArrayKey
}
