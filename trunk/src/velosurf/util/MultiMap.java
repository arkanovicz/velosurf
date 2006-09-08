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

import java.util.List;
import java.util.Set;

/** 
 * A MultiMap is a Map allowing multiple occurrences of keys.
 *
 * @version 	$ProjectVersion: 0.207 $
 * @author 	Mark Richters
 * @see         java.util.Map
 */

public interface MultiMap {
    // Query Operations

    /**
     * Returns the number of values in this multimap.
     */
    int size();

    /**
     * Returns <tt>true</tt> if this multimap contains no mappings.  
     */
    boolean isEmpty();

    /**
     * Returns <tt>true</tt> if this multimap contains a mapping for
     * the specified key.  
     */
    boolean containsKey(Object key);

    /**
     * Returns <tt>true</tt> if this multimap maps one or more keys to
     * the specified value.  
     */
    boolean containsValue(Object value);

    /**
     * Returns a list of values to which this multimap maps the specified
     * key.  
     *
     * @return the list of values to which this map maps the specified
     *         key, the list may be empty if the multimap contains no
     *         mapping for this key. 
     */
    List get(Object key);

    // Modification Operations

    /**
     * Adds the specified value with the specified key to this multimap.
     */
    void put(Object key, Object value);

    /**
     * Copies all entries from the specified multimap to this
     * multimap.  
     */
    void putAll(MultiMap t);

    /**
     * Removes all mappings for this key from this multimap if present.
     */
    void remove(Object key);

    /**
     * Removes the specified key/value mapping from this multimap if present.
     */
    void remove(Object key, Object value);

    // Bulk Operations

    /**
     * Removes all mappings from this map (optional operation).
     */
    void clear();

    // Views

    /**
     * Returns a set view of the keys contained in this multimap.
     */
    Set keySet();

    /**
     * Returns a collection view of the values contained in this map.
     */
    //Collection values();

    // Comparison and hashing

    /**
     * Compares the specified object with this multimap for equality.
     */
    boolean equals(Object o);

    /**
     * Returns the hash code value for this map.
     */
    int hashCode();
}
