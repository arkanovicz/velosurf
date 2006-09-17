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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A MultiMap is a Map allowing multiple occurrences of keys.
 *
 * @author Mark Richters
 * @see java.util.Map
 * @version $ProjectVersion: 0.207 $
 */

public class HashMultiMap implements MultiMap {
    /** inner map
     */
    private Map fMap;        // (Object key -> List values)
    /** total number of values
     */
    private transient int fSizeAll;

    /** builds a new HashMultiMap
     */
    public HashMultiMap() {
    fMap = new HashMap();
    }

    // Query Operations

    /**
     * Returns the number of values in this multimap.
     *
     * @return totla number of values
     */
    public int size() {
    return fSizeAll;
    }

    /**
     * Returns <tt>true</tt> if this multimap contains no mappings.
     *
     * @return empty status
     */
    public boolean isEmpty() {
    return fSizeAll == 0;
    }

    /**
     * Returns <tt>true</tt> if this multimap contains a mapping for
     * the specified key.
     *
     * @param key
     */
    public boolean containsKey(Object key) {
    return fMap.containsKey(key);
    }

    /**
     * Returns <tt>true</tt> if this multimap maps one or more keys to
     * the specified value.
     */
    public boolean containsValue(Object value) {
    Iterator it = fMap.values().iterator();
    while ( it.hasNext() ) {
        List l = (List) it.next();
        if ( l.contains(value) )
        return true;
    }
    return false;
    }

    /**
     * Returns a list of values to which this multimap maps the specified
     * key.
     *
     * @return the list of values to which this map maps the specified key, the
     *     list may be empty if the multimap contains no mapping for this key.
     */
    public List get(Object key) {
    List l = (List) fMap.get(key);
    if ( l == null )
        l = new ArrayList();
    return l;
    }

    // Modification Operations

    /**
     * Adds the specified value with the specified key to this multimap.
     */
    public void put(Object key, Object value) {
    List l = (List) fMap.get(key);
    if ( l == null ) {
        l = new ArrayList();
        fMap.put(key, l);
    }
    l.add(value);
    fSizeAll++;
    }

    /**
     * Copies all entries from the specified multimap to this
     * multimap.
     */
    public void putAll(MultiMap t) {
        
    Iterator it = t.keySet().iterator();
    while ( it.hasNext() ) {
        Object key = it.next();
        List tl = (List) t.get(key);

        List l = (List) fMap.get(key);
        if ( l == null ) {
        l = new ArrayList();
        fMap.put(key, l);
        }
        l.addAll(tl);
        fSizeAll += tl.size();
    }
    }

    /**
     * Removes all mappings for this key from this multimap if present.
     */
    public void remove(Object key) {
    List l = (List) fMap.get(key);
    if ( l != null )
        fSizeAll -= l.size();
    fMap.remove(key);
    }

    /**
     * Removes the specified key/value mapping from this multimap if present.
     */
    public void remove(Object key, Object value) {
    List l = (List) fMap.get(key);
    if ( l != null )
        if ( l.remove(value) )
        fSizeAll--;
    }

    // Bulk Operations

    /**
     * Removes all mappings from this map (optional operation).
     */
    public void clear() {
    fMap.clear();
    fSizeAll = 0;
    }

    // Views

    /**
     * Returns a set view of the keys contained in this multimap.
     */
    public Set keySet() {
    return fMap.keySet();
    }

    // Comparison and hashing

    /**
     * Compares the specified object with this multimap for equality.
     */
    public boolean equals(Object o) {
    if ( o == this )
        return true;

    // FIXME: use MultiMap interface only
    if ( ! (o instanceof HashMultiMap) )
        return false;
    HashMultiMap c = (HashMultiMap) o;
    if ( c.size() != size() )
        return false;

    return fMap.equals(c.fMap);
    }

    /**
     * Returns the hash code value for this multimap.
     */
    public int hashCode() {
    int h = 0;
    Iterator it = fMap.entrySet().iterator();
    while ( it.hasNext() ) {
        Object obj = it.next();
        h += obj.hashCode();
        }
    return h;
    }
}
