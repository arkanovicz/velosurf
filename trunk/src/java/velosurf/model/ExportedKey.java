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

package velosurf.model;

import java.util.List;

import velosurf.util.StringLists;
import velosurf.util.Logger;

/** An exported key (aka primary key used in a foreign key) attribute.
 *
 */

public class ExportedKey extends Attribute {

    /**
     * List of foreign keys.
     */
    private List<String> fkCols = null;

    /**
     * Exported key constructor.
     * @param name name of this exported key
     * @param entity parent entity
     * @param fkEntity foreign key entity
     * @param fkCols foreign key columns
     */
    public ExportedKey(String name,Entity entity,String fkEntity,List<String> fkCols) {
        super(name,entity);
        setResultType(Attribute.ROWSET);
        setResultEntity(fkEntity);
        this.fkCols = fkCols; /* may still be null at this stage */
    }

    /**
     * Foreign key columns getter.
     * @return foreign key columns list
     */
    public List<String> getFKCols() {
        return fkCols;
    }

    /**
     * Foreign key columns setter.
     * @param fkCols foreign key columns list
     */
    public void setFKCols(List<String> fkCols) {
        this.fkCols = fkCols;
    }

    /**
     * Query getter.
     * @return the SQL query
     */
    protected String getQuery() {
        if(query == null) {
            Entity fkEntity = db.getEntity(resultEntity);
            for(String param:getEntity().getPKCols()) {
                addParamName(param);
            }
            query = "SELECT * FROM " + fkEntity.getTableName() + " WHERE " + StringLists.join(fkEntity.aliasToColumn(fkCols)," = ? AND ") + " = ?";
//          Logger.debug(getEntity().getName()+"."+getName()+" = "+query+" [ with params "+StringLists.join(getEntity().getPKCols(),",")+" ]" );
        }
        return query;
    }

    /** Debug method.
     *
     * @return the definition string of this attribute
     */
    public String toString() {
        return "exported-key"+(fkCols==null?"":" on "+StringLists.join(fkCols,","));
    }

}
