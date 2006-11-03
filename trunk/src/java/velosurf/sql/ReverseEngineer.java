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
    private Map<String,String> mEntityByTableName = new HashMap<String,String>();

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
        if(mDatabase.getSchema() != null) {
            tableName = mDatabase.getSchema()+"."+tableName;
        }
        mEntityByTableName.put(tableName,entity.getName());
    }

    /** read the meta data from the database : reverse engeenering
     *
     * @exception java.sql.SQLException thrown by the database engine
     */
    protected void readMetaData() throws SQLException {

        DatabaseMetaData meta = mDatabase.getConnection().getMetaData();
        ResultSet tables = null;

        // extra debug information about which jdbc driver we are using
        Logger.info("Database Product Name:    " + meta.getDatabaseProductName());
        Logger.info("Database Product Version: " + meta.getDatabaseProductVersion());
        Logger.info("JDBC Driver Name:         " + meta.getDriverName());
        Logger.info("JDBC Driver Version:      " + meta.getDriverVersion());

        /* columns and primary keys */
        try {
            Logger.debug("reverse enginering: mode = "+sReverseModeName[mReverseMode]);
            if(mReverseMode == REVERSE_NONE) {
                return;
            }

            switch(mReverseMode)
            {
                case REVERSE_TABLES:
                case REVERSE_FULL:
                    break;
                case REVERSE_PARTIAL:
                    break;
            }

            switch(mReverseMode)
            {
                case REVERSE_TABLES:
                case REVERSE_FULL:
                    tables = meta.getTables(null,mDatabase.getSchema(),null,null);
                    while (tables.next()) {
                        String tableName = adaptCase(tables.getString("TABLE_NAME"));
                        if (mDriverInfo.ignoreTable(tableName)) {
                            continue;
                        }
                        if(mDatabase.getSchema() != null) {
                            tableName = mDatabase.getSchema() + tableName;
                        }
                        String entityName = mEntityByTableName.get(tableName);
                        Entity entity = null;
                        if(entityName != null) {
                            entity = mDatabase.getEntity(entityName);
                        }
                        if (entity == null) {
                            entity = mDatabase.getEntityCreate(tableName);
                            mEntityByTableName.put(tableName,entity.getName());
                        }
                        readTableMetaData(meta,entity,tableName);
                    }
/* not any more valid since mEntityByTableName does now keep mappings
                    for(Iterator e = mEntityByTableName.keySet().iterator();e.hasNext();)
                    {
                        Logger.warn("table '"+(String)e.next()+"' not found!");
                    }
*/
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
        } finally {
            if (tables != null) tables.close();
        }

        /* imported and exported keys */
        if(mReverseMode == REVERSE_FULL) {
            try {
                tables = meta.getTables(null,mDatabase.getSchema(),null,null);
                while (tables.next()) {
                    String tableName = adaptCase(tables.getString("TABLE_NAME"));
                    if (mDriverInfo.ignoreTable(tableName)) {
                        continue;
                    }
                    if(mDatabase.getSchema() != null) {
                        tableName = mDatabase.getSchema() + tableName;
                    }
                    String entityName = mEntityByTableName.get(tableName);
                    Entity entity = mDatabase.getEntity(entityName);

                    readForeignKeys(meta,entity,tableName);
                }
            } finally    {
                if (tables != null) tables.close();
            }
        }

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
                entity.addPKColumn((String)keylist.get(k));
            }
        }
        finally    {
            if (pks != null) pks.close();
        }

        entity.reverseEnginered();
    }

    private void readForeignKeys(DatabaseMetaData meta,Entity entity,String tableName) throws SQLException {
        /* read imported keys */
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
                            addImportedKey(entity,pkSchema,pkTable,pkCols,fkCols);
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
                    addImportedKey(entity,pkSchema,pkTable,pkCols,fkCols);
                }
            }
            finally {
                if (iks != null) iks.close();
            }
        }

        /* read exported keys */
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
                            addExportedKey(entity,fkSchema,fkTable,fkCols,pkCols);
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
                    addExportedKey(entity,fkSchema,fkTable,fkCols,pkCols);
                }
            }
            finally {
                if (eks != null) eks.close();
            }
        }
    }

    private void addExportedKey(Entity entity, String fkSchema, String fkTable, List<String> fkCols, List<String> pkCols) {
        String fkEntityName = getEntityByTable(fkSchema,fkTable);
        if (fkEntityName == null) {
            Logger.warn("reverse: ignoring exported key "+fkSchema+"."+fkTable+": corresponding entity not found.");
        } else {
            if (fkSchema != null) {
                fkTable = fkSchema+"."+fkTable;
            }
            Entity fkEntity = mDatabase.getEntity(fkEntityName);
            fkCols = sortColumns(entity.getPKCols(),pkCols,fkCols);
            Logger.trace("reverse: found exported key: "+entity.getName()+"("+StringLists.join(pkCols,",")+") <- "+fkTable+"("+StringLists.join(fkCols,",")+")");
            ExportedKey definedKey = entity.findExportedKey(fkEntity,fkCols);
            if (definedKey == null) {
                entity.addAttribute(new ExportedKey(getExportedKeyName(fkEntityName),entity,fkTable,fkCols));                
            } else if (definedKey.getFKCols() == null) {
                definedKey.setFKCols(fkCols);
            }
        }
    }

    private void addImportedKey(Entity entity,String pkSchema, String pkTable, List<String> pkCols, List<String> fkCols) {
        String pkEntityName = getEntityByTable(pkSchema,pkTable);
        if(pkEntityName == null) {
            Logger.warn("reverse: ignoring imported key "+pkSchema+"."+pkTable+": corresponding entity not found.");
        } else {
            if(pkSchema != null) {
                pkTable = pkSchema+"."+pkTable;
            }
            Entity pkEntity = mDatabase.getEntity(pkEntityName);
            fkCols = sortColumns(pkEntity.getPKCols(),pkCols,fkCols);
            Logger.trace("reverse: found imported key: "+entity.getName()+"("+StringLists.join(fkCols,",")+") -> "+pkTable+"("+StringLists.join(pkCols,",")+")");
            ImportedKey definedKey = entity.findImportedKey(pkEntity,fkCols);
            if (definedKey == null) {
                entity.addAttribute(new ImportedKey(pkEntityName,entity,pkEntityName,fkCols));
            } else if (definedKey.getFKCols() == null) {
                    definedKey.setFKCols(fkCols);
            }
        }
    }

    /* TODO review case */
    private String getEntityByTable(String schema,String table) {
        String entityName;
        if (schema == null) {
            if (mDatabase.getSchema() == null) {
                entityName = mEntityByTableName.get(table);
            } else {
                /* buggy jdbc driver */
                entityName = mEntityByTableName.get(mDatabase.getSchema()+"."+table);
            }
        } else {
            if (mDatabase.getSchema() == null) {
                /* means the jdbc driver is using a default schema name,
                 * like PUBLIC for hsqldb (OR that we want an instance in another database connexion. TODO) */
                entityName = mEntityByTableName.get(schema+"."+table);
                if (entityName == null) {
                    entityName = mEntityByTableName.get(table);
                }
            } else {
                /* TODO what if not equal? */
                entityName = mEntityByTableName.get(schema+"."+table);
            }
        }
        return entityName;
    }

    private List<String> sortColumns(List<String> ordered,List<String> unordered,List<String>target) {
        if(ordered.size() == 1) {
            return target;
        }
        List<String>sorted = new ArrayList<String>();
        for(String col:ordered) {
            int i = unordered.indexOf(col);
            assert(i!=-1);
            sorted.add(target.get(i));
        }
        return sorted;
    }

    private String adaptCase(String name) {
        return mDatabase.adaptCase(name);
    }

    private String getExportedKeyName(String name) {
        return adaptCase(name.endsWith("s")? name+"es" : name+"s");
    }
}
