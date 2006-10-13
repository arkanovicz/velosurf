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
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.sql.SQLException;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import velosurf.model.Entity;
import velosurf.util.Logger;
import velosurf.util.StringLists;

/**
 * Class used to reverse engine a database
 */
public class ReverseEngineer {

    /** map table->entity, valid only between readConfigFile and readMetaData
    */
    private Map<String,Entity> mEntityByTableName = new HashMap<String,Entity>();

    private Database mDatabase;
    private DriverInfo mDriverInfo;

    private int mReverseMode = REVERSE_NONE;

    /** reverse-enginering modes */
    public static final int REVERSE_NONE = 1;
    public static final int REVERSE_PARTIAL = 2;
    public static final int REVERSE_FULL = 3;

    /** constructor
     *
     */
    public ReverseEngineer(Database database) {
        mDatabase = database;
    }

    protected void setDriverInfo(DriverInfo di) {
        mDriverInfo = di;
    }

    protected void setReverseMode(int reverseMethod) {
        mReverseMode = reverseMethod;
    }

    protected void addCorrespondance(String tableName,Entity entity) {
        mEntityByTableName.put(tableName,entity);
    }

    /** read the meta data from the database : reverse engeenering
     *
     * @exception java.sql.SQLException thrown by the database engine
     */
    protected void readMetaData() throws SQLException {

        DatabaseMetaData meta = mDatabase.getConnection().getMetaData();
        ResultSet tables = null;

        // perform the reverse enginering
        try    {

            switch(mReverseMode)
            {
                case REVERSE_FULL:
                    tables = meta.getTables(null,mDatabase.getSchema(),null,null);
                    while (tables.next()) {
                        String tableName = mDatabase.adaptCase(tables.getString("TABLE_NAME"));
                        if (tableName.indexOf('/')!=-1) {
                            /* Oracle system tables (hack) */
                            continue; // skip special tables (Oracle)
                        }
                        if (tableName.startsWith(mDriverInfo.getIgnorePrefix())) {
                            continue;
                        }
                        Entity entity = (Entity)mEntityByTableName.get(tableName);
                        if (entity == null) entity = mDatabase.getEntityCreate(mDatabase.adaptCase(tableName));
                        else mEntityByTableName.remove(tableName);
                        readTableMetaData(meta,entity,tableName);
                    }
                    for(Iterator e = mEntityByTableName.keySet().iterator();e.hasNext();)
                    {
                        Logger.warn("table '"+(String)e.next()+"' not found!");
                    }
                    break;
                case REVERSE_PARTIAL:
                    for(Entity entity:mDatabase.getEntities().values()) {
                        String tableName = entity.getTableName();
                        readTableMetaData(meta,entity,tableName);
                    }
                    break;
                case REVERSE_NONE:
                    break;
            }
        }
        finally    {
            if (tables != null) tables.close();
        }
        // extra debug information about which jdbc driver we are using
        Logger.info("Database Product Name:    " + meta.getDatabaseProductName());
        Logger.info("Database Product Version: " + meta.getDatabaseProductVersion());
        Logger.info("JDBC Driver Name:         " + meta.getDriverName());
        Logger.info("JDBC Driver Version:      " + meta.getDriverVersion());
    }

    private void readTableMetaData(DatabaseMetaData meta,Entity entity,String tableName) throws SQLException {

        List keylist = StringLists.getPopulatedArrayList(10); // ever seen a primary key with more than 10 columns ?!

        // read columns
        ResultSet cols = null;
        try    {
            cols = meta.getColumns(null,mDatabase.getSchema(),tableName,null);
            while (cols.next()) {
                String column = mDatabase.adaptCase(cols.getString("COLUMN_NAME"));
                entity.addColumn(column);
            }
        }
        finally {
            if (cols != null) cols.close();
        }

        // read primary keys
        ResultSet pks = null;
        try    {
            pks = meta.getPrimaryKeys(null,mDatabase.getSchema(),tableName);
            int keysize = 0;
            while (pks.next()) {
                short ord = pks.getShort("KEY_SEQ");
                String column = mDatabase.adaptCase(pks.getString("COLUMN_NAME"));
                keylist.set(ord-1,column);
                keysize++;
            }
            for (int k=0;k<keysize;k++) {
                entity.addKey((String)keylist.get(k));
            }
        }
        finally    {
            if (pks != null) pks.close();
            entity.reverseEnginered();
        }
    }
}
