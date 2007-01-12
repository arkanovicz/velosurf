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

/** An imported key (aka foreign key) attribute.
 *
 */

public class ImportedKey extends Attribute {

    /**
     * Foreign key columns.
     */
    private List<String> fkCols = null;

    /** Imported key constructor.
     *
     * @param name name of this exported key
     * @param entity parent entity
     * @param pkEntity primary key entity
     * @param fkCols foreign key columns
     */
    public ImportedKey(String name,Entity entity,String pkEntity,List<String> fkCols) {
        super(name,entity);
        setResultEntity(pkEntity);
        this.fkCols = fkCols; /* may still be null at this stage */
        setResultType(Attribute.ROW);
    }

    /**
     * Query getter.
     * @return SQL query
     */
    protected String getQuery()
    {
        if(query == null) {
            Entity pkEntity = db.getEntity(resultEntity);
            for(String param:fkCols) {
                addParamName(param);
            }
            query = "SELECT * FROM " + pkEntity.getTableName() + " WHERE " + StringLists.join(pkEntity.getPKCols()," = ? AND ") + " = ?";
//          Logger.debug(getEntity().getName()+"."+getName()+" = "+query+" [ with params "+StringLists.join(fkCols,",")+" ]" );
        }
        return query;
    }

    /**
     * Foreign key columns getter.
     * @return foreign key columns list
     */
    public List<String> getFKCols() {
        return fkCols;
    }

    /** Foreign key columns setter.
     *
     * @param fkCols foreign key columns list
     */
    public void setFKCols(List<String> fkCols) {
        this.fkCols = fkCols;
    }

    /** Debug method.
     *
     * @return the definition string of this attribute
     */
    public String toString() {
        return "imported-key"+(fkCols==null?"":" on "+StringLists.join(fkCols,","));
    }

}
