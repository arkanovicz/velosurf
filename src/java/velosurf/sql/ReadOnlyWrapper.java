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
     * @param inInnerMap the Map object to encapsulate
     */
    public ReadOnlyWrapper(Map inInnerMap){
        mInnerMap = inInnerMap;
    }
    /** get the property named inKey in the wrapped Map
     *
     * @param inKey the name of the property
     * @return the property value or null if not found
     * @see velosurf.sql.ReadOnlyMap#get(java.lang.Object)
     */
    public Object get(Object inKey) {
        return mInnerMap.get(inKey);
    }

    public Set keySet() throws SQLException {
        return mInnerMap.keySet();
    }

    /** the wrapped Map
     */
    protected Map mInnerMap = null;
}
