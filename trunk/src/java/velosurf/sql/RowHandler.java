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
import java.util.Set;

/**
 * This interface represents objects having read-only properties
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 *
 */
public interface RowHandler
{
    /**
     * get the property named key.
     *
     * @param key the name of the property to return
     * @return the value of the property, or null if not found
     */
    public Serializable get(Object key) throws SQLException;

    /**
     * Get keys set.
     * @return keys set
     */
    public Set<String> keySet() throws SQLException;
}
