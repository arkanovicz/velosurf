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
import java.util.ArrayList;
import java.sql.SQLException;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

import velosurf.model.Entity;
import velosurf.model.ImportedKey;
import velosurf.model.ExportedKey;
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

    private int mReverseMode = DEFAULT_REVERSE_MODE;

    /** reverse-enginering modes */
    public static final int REVERSE_NONE = 0;
    public static final int REVERSE_PARTIAL = 1;
    public static final int REVERSE_TABLES = 2;
    public static final int REVERSE_FULL = 3;

    public static final int DEFAULT_REVERSE_MODE = REVERSE_FULL;

    public static String[] sReverseModeName = {
        "none",
        "partial",
        "tables",
        "full"
    };

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
            Logger.debug("reverse enginering: mode = "+sReverseModeName[mReverseMode]);
            switch(mReverseMode)
            {
                case REVERSE_TABLES:
                case REVERSE_FULL:
                    tables = meta.getTables(null,mDatabase.getSchema(),null,null);
                    while (tables.next()) {
                        String tableName = adaptCase(tables.getString("TABLE_NAME"));
                        if (tableName.indexOf('/')!=-1) {
                            /* Oracle system tables (hack) */
                            continue; // skip special tables (Oracle)
                        }
                        if (mDriverInfo.ignoreTable(tableName)) {
                            continue;
                        }
                        Entity entity = (Entity)mEntityByTableName.get(tableName);
                        if (entity == null) entity = mDatabase.getEntityCreate(adaptCase(tableName));
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

        /* read columns */
        ResultSet cols = null;
        try {
            cols = meta.getColumns(null,mDatabase.getSchema(),tableName,null);
            while (cols.next()) {
                String column = adaptCase(cols.getString("COLUMN_NAME"));
                entity.addColumn(column);
            }
        }
        finally {
            if (cols != null) cols.close();
        }

        /* read primary keys */
        ResultSet pks = null;
        try    {
            pks = meta.getPrimaryKeys(null,mDatabase.getSchema(),tableName);
            int keysize = 0;
            while (pks.next()) {
                short ord = pks.getShort("KEY_SEQ");
                String column = adaptCase(pks.getString("COLUMN_NAME"));
                keylist.set(ord-1,column);
                keysize++;
            }
            for (int k=0;k<keysize;k++) {
                entity.addKey((String)keylist.get(k));
            }
        }
        finally    {
            if (pks != null) pks.close();
        }

        /* read imported keys */ /* TODO: skip already defined keys (compare key cols) */
        if (mReverseMode == REVERSE_FULL) {
            ResultSet iks = null;
            try {
                iks = meta.getImportedKeys(null,mDatabase.getSchema(),tableName);
                short ord;
                String pkSchema = null,pkTable = null;
                List<String> fkCols = new ArrayList<String>();
                List<String> pkCols = new ArrayList<String>();
                while(iks.next()) {
                    ord = iks.getShort("KEY_SEQ");
                    if(ord == 1) {
                        /* save previous key */
                        if (fkCols.size() > 0) {
                            pkTable = pkSchema+"."+pkTable;
                            entity.addAttribute(new ImportedKey(pkTable,entity,pkTable,pkCols,fkCols));
                            fkCols.clear();
                            pkCols.clear();
                        }
                        pkSchema = adaptCase(iks.getString("PKTABLE_SCHEM"));
                        pkTable = adaptCase(iks.getString("PKTABLE_NAME"));
                    }
                    pkCols.add(adaptCase(iks.getString("PKCOLUMN_NAME")));
                    fkCols.add(adaptCase(iks.getString("FKCOLUMN_NAME")));
                }
                /* save last key */
                if (pkCols.size() > 0) {
                    Logger.trace("reverse: found imported key: "+tableName+"("+StringLists.join(fkCols,",")+") -> "+pkTable+"("+StringLists.join(pkCols,",")+")");
                    entity.addAttribute(new ImportedKey(pkTable,entity,pkTable,pkCols,fkCols));
                }
            }
            finally {
                if (iks != null) iks.close();
            }
        }

        /* read exported keys */ /* TODO: skip already defined keys (compare key cols) */
        if (mReverseMode == REVERSE_FULL) {
            ResultSet eks = null;
            try {
                eks = meta.getExportedKeys(null,mDatabase.getSchema(),tableName);
                short ord;
                String fkSchema = null,fkTable = null;
                List<String> fkCols = new ArrayList<String>();
                List<String> pkCols = new ArrayList<String>();
                while(eks.next()) {
                    ord = eks.getShort("KEY_SEQ");
                    if(ord == 1) {
                        /* save previous key */
                        if (fkCols.size() > 0) {
                            if (fkSchema != null) {
                                fkTable = fkSchema+"."+fkTable;
                            }
                            /* TODO review name! */
                            entity.addAttribute(new ExportedKey(getExportedKeyName(fkTable),entity,fkTable,fkCols,pkCols));
                            fkCols.clear();
                            pkCols.clear();
                        }
                        fkSchema = adaptCase(eks.getString("FKTABLE_SCHEM"));
                        fkTable = adaptCase(eks.getString("FKTABLE_NAME"));
                    }
                    pkCols.add(adaptCase(eks.getString("PKCOLUMN_NAME")));
                    fkCols.add(adaptCase(eks.getString("FKCOLUMN_NAME")));
                }
                /* save last key */
                if (pkCols.size() > 0) {
                    Logger.trace("reverse: found exported key: "+tableName+"("+StringLists.join(pkCols,",")+") <- "+fkTable+"("+StringLists.join(fkCols,",")+")");
                    entity.addAttribute(new ExportedKey(getExportedKeyName(fkTable),entity,fkTable,fkCols,pkCols));
                }
            }
            finally {
                if (eks != null) eks.close();
            }
        }

        entity.reverseEnginered();
    }

    private String adaptCase(String name) {
        return mDatabase.adaptCase(name);
    }

    private String getExportedKeyName(String name) {
        return adaptCase(name.endsWith("s")? name+"es" : name+"s");
    }
}
