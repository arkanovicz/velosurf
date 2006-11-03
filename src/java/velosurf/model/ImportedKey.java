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
import java.sql.SQLException;

import velosurf.util.StringLists;

/** An imported key (aka foreign key) attribute
 *
 */

public class ImportedKey extends Attribute {

    private List<String> fkCols = null;

    public ImportedKey(String name,Entity entity,String pkEntity,List<String> fkCols) {
        super(name,entity);
        setResultEntity(pkEntity);
        this.fkCols = fkCols; /* may still be null at this stage */
        setResultType(Attribute.ROW);
    }

    protected String getQuery() throws SQLException
    {
        if(mQuery == null) {
            Entity pkEntity = mDB.getEntity(mResultEntity);
            for(String param:fkCols) {
                addParamName(param);
            }
            mQuery = "SELECT * FROM " + pkEntity.getTableName() + " WHERE " + StringLists.join(pkEntity.getPKCols()," = ? AND ") + " = ?";
        }
        return mQuery;
    }

    public List<String> getFKCols() {
        return fkCols;
    }

    public void setFKCols(List<String> fkCols) {
        this.fkCols = fkCols;
    }
}
