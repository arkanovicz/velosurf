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

/** An exported key (aka primary key used in a foreign key) attribute
 *
 */

public class ExportedKey extends Attribute {

    private List<String> fkCols = null;

    public ExportedKey(String name,Entity entity,String pkEntity,List<String> fkCols) {
        super(name,entity);
        setResultType(Attribute.ROWSET);
        setResultEntity(pkEntity);
        this.fkCols = fkCols; /* may still be null at this stage */
    }

    public List<String> getFKCols() {
        return fkCols;
    }

    public void setFKCols(List<String> fkCols) {
        this.fkCols = fkCols;
    }

    public String getQuery() {
        if(mQuery == null) {
            Entity fkEntity = mDB.getEntity(mResultEntity);
            for(String param:mDB.getEntity(mResultEntity).getPKCols()) {
                addParamName(param);
            }
            mQuery = "SELECT * FROM " + fkEntity.getTableName() + " WHERE " + StringLists.join(fkEntity.aliasToColumn(fkCols)," = ? AND ") + " = ?";
        }
        return mQuery;
    }
}
