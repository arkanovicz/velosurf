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

import java.util.Map;
import java.util.Set;
import java.sql.SQLException;

/** This class encapsulates a Map as a DataAccessor
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 */
public class ReadOnlyWrapper implements ReadOnlyMap {

    /** builds a new MapDataAccessor
     *
     * @param innerMap the Map object to encapsulate
     */
    public ReadOnlyWrapper(Map innerMap){
        this.innerMap = innerMap;
    }
    /** get the property named key in the wrapped Map
     *
     * @param key the name of the property
     * @return the property value or null if not found
     * @see velosurf.sql.ReadOnlyMap#get(java.lang.Object)
     */
    public Object get(Object key) {
        return innerMap.get(key);
    }

    public Set keySet() throws SQLException {
        return innerMap.keySet();
    }

    /** the wrapped Map
     */
    protected Map innerMap = null;
}
