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
 * Class used to reverse engine a database.
 */
public class ReverseEngineer {

    /** map table->entity, valid only between readConfigFile and readMetaData.
    */
    private Map<String,String> entityByTableName = new HashMap<String,String>();
    /** Database. */
    private Database db;
    /** Driver infos. */
    private DriverInfo driverInfo;
    /** Reverse mode. */
    private int reverseMode = DEFAULT_REVERSE_MODE;
    /** No reverse engineering mode. */
    public static final int REVERSE_NONE = 0;
    /** Partial reverse engineering mode. */
    public static final int REVERSE_PARTIAL = 1;
    /** Tables reverse engineering mode. */
    public static final int REVERSE_TABLES = 2;
    /** Full reverse engineering mode. */
    public static final int REVERSE_FULL = 3;
    /** Default reverse engineering mode. */
    public static final int DEFAULT_REVERSE_MODE = REVERSE_FULL;
    /** Reverse engineering mode names. */
    public static String[] reverseModeName = {
        "none",
        "partial",
        "tables",
        "full"
    };

    /** constructor.
     *
     * @param database database
     */
    public ReverseEngineer(Database database) {
        db = database;
    }
    /** Driver infos setter.
     *
     * @param di driver infos
     */
    protected void setDriverInfo(DriverInfo di) {
        driverInfo = di;
    }
    /**
     * Set reverse mode.
     * @param reverseMethod reverse mode
     */
    protected void setReverseMode(int reverseMethod) {
        reverseMode = reverseMethod;
    }
    /**
     * add a table &lt;-&gt; entity matching.
     * @param tableName
     * @param entity
     */
    protected void addTableMatching(String tableName,Entity entity) {
        if(db.getSchema() != null) {
            tableName = db.getSchema()+"."+tableName;
        }
        entityByTableName.put(tableName,entity.getName());
    }

    /** read the meta data from the database.
     *
     * @exception java.sql.SQLException thrown by the database engine
     */
    protected void readMetaData() throws SQLException {

        DatabaseMetaData meta = db.getConnection().getMetaData();
        ResultSet tables = null;

        // extra debug information about which jdbc driver we are using
        Logger.info("Database Product Name:    " + meta.getDatabaseProductName());
        Logger.info("Database Product Version: " + meta.getDatabaseProductVersion());
        Logger.info("JDBC Driver Name:         " + meta.getDriverName());
        Logger.info("JDBC Driver Version:      " + meta.getDriverVersion());

        /* columns and primary keys */
        try {
            Logger.debug("reverse enginering: mode = "+reverseModeName[reverseMode]);
            if(reverseMode == REVERSE_NONE) {
                return;
            }

            switch(reverseMode)
            {
                case REVERSE_TABLES:
                case REVERSE_FULL:
                    break;
                case REVERSE_PARTIAL:
                    break;
            }

            switch(reverseMode)
            {
                case REVERSE_TABLES:
                case REVERSE_FULL:
                    tables = meta.getTables(null,db.getSchema(),null,null);
                    while (tables.next()) {
                        String tableName = adaptCase(tables.getString("TABLE_NAME"));
                        if (driverInfo.ignoreTable(tableName)) {
                            continue;
                        }
                        if(db.getSchema() != null) {
                            tableName = db.getSchema() + tableName;
                        }
                        String entityName = entityByTableName.get(tableName);
                        Entity entity = null;
                        if(entityName != null) {
                            entity = db.getEntity(entityName);
                        }
                        if (entity == null) {
                            entity = db.getEntityCreate(tableName);
                            entityByTableName.put(tableName,entity.getName());
                        }
                        readTableMetaData(meta,entity,tableName);
                    }
/* not any more valid since entityByTableName does now keep mappings TODO alternative
                    for(Iterator e = entityByTableName.keySet().iterator();e.hasNext();)
                    {
                        Logger.warn("table '"+(String)e.next()+"' not found!");
                    }
*/
                    break;
                case REVERSE_PARTIAL:
                    for(Entity entity:db.getEntities().values()) {
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
        if(reverseMode == REVERSE_FULL) {
            try {
                tables = meta.getTables(null,db.getSchema(),null,null);
                while (tables.next()) {
                    String tableName = adaptCase(tables.getString("TABLE_NAME"));
                    if (driverInfo.ignoreTable(tableName)) {
                        continue;
                    }
                    if(db.getSchema() != null) {
                        tableName = db.getSchema() + tableName;
                    }
                    String entityName = entityByTableName.get(tableName);
                    Entity entity = db.getEntity(entityName);

                    readForeignKeys(meta,entity,tableName);
                }
            } finally    {
                if (tables != null) tables.close();
            }
        }

    }
    /** Read table meta data.
     *
     * @param meta database meta data
     * @param entity corresponding entity
     * @param tableName table name
     * @throws SQLException
     */
    private void readTableMetaData(DatabaseMetaData meta,Entity entity,String tableName) throws SQLException {

        List<String> keylist = StringLists.getPopulatedArrayList(10); // ever seen a primary key with more than 10 columns ?!

        /* read columns */
        ResultSet cols = null;
        try {
            cols = meta.getColumns(null,db.getSchema(),tableName,null);
            while (cols.next()) {
                String column = adaptCase(cols.getString("COLUMN_NAME"));
                entity.addColumn(column,cols.getInt("DATA_TYPE"));
            }
        }
        finally {
            if (cols != null) cols.close();
        }

        /* read primary keys */
        ResultSet pks = null;
        try    {
            pks = meta.getPrimaryKeys(null,db.getSchema(),tableName);
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
    /**
     * Read foreign keys.
     * @param meta database meta data
     * @param entity entity
     * @param tableName table name
     * @throws SQLException
     */
    private void readForeignKeys(DatabaseMetaData meta,Entity entity,String tableName) throws SQLException {
        /* read imported keys */
        if (reverseMode == REVERSE_FULL) {
            ResultSet iks = null;
            try {
                iks = meta.getImportedKeys(null,db.getSchema(),tableName);
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
        if (reverseMode == REVERSE_FULL) {
            ResultSet eks = null;
            try {
                eks = meta.getExportedKeys(null,db.getSchema(),tableName);
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
    /**
     * Add a new exported key.
     * @param entity target entity
     * @param fkSchema foreign key schema
     * @param fkTable foreign key table
     * @param fkCols foreign key columns
     * @param pkCols primary key columns
     */
    private void addExportedKey(Entity entity, String fkSchema, String fkTable, List<String> fkCols, List<String> pkCols) {
        String fkEntityName = getEntityByTable(fkSchema,fkTable);
        if (fkEntityName == null) {
            Logger.warn("reverse: ignoring exported key "+fkSchema+"."+fkTable+": corresponding entity not found.");
        } else {
            if (fkSchema != null) {
                fkTable = fkSchema+"."+fkTable;
            }
            Entity fkEntity = db.getEntity(fkEntityName);
            fkCols = sortColumns(entity.getPKCols(),pkCols,fkCols);
//            Logger.trace("reverse: found exported key: "+entity.getName()+"("+StringLists.join(pkCols,",")+") <- "+fkTable+"("+StringLists.join(fkCols,",")+")");
            ExportedKey definedKey = entity.findExportedKey(fkEntity,fkCols);
            if (definedKey == null) {
                entity.addAttribute(new ExportedKey(getExportedKeyName(fkEntityName),entity,fkTable,new ArrayList<String>(fkCols)));
            } else if (definedKey.getFKCols() == null) {
                definedKey.setFKCols(fkCols);
            }
        }
    }
    /**
     * Add a new imported key.
     * @param entity target entity
     * @param pkSchema primary key schema
     * @param pkTable primary key table
     * @param pkCols primary key columns
     * @param fkCols foreign key columns
     */
    private void addImportedKey(Entity entity,String pkSchema, String pkTable, List<String> pkCols, List<String> fkCols) {
        String pkEntityName = getEntityByTable(pkSchema,pkTable);
        if(pkEntityName == null) {
            Logger.warn("reverse: ignoring imported key "+pkSchema+"."+pkTable+": corresponding entity not found.");
        } else {
            if(pkSchema != null) {
                pkTable = pkSchema+"."+pkTable;
            }
            Entity pkEntity = db.getEntity(pkEntityName);
            fkCols = sortColumns(pkEntity.getPKCols(),pkCols,fkCols);
  //          Logger.trace("reverse: found imported key: "+entity.getName()+"("+StringLists.join(fkCols,",")+") -> "+pkTable+"("+StringLists.join(pkCols,",")+")");
            ImportedKey definedKey = entity.findImportedKey(pkEntity,fkCols);
            if (definedKey == null) {
                entity.addAttribute(new ImportedKey(pkEntityName,entity,pkEntityName,new ArrayList<String>(fkCols)));
            } else if (definedKey.getFKCols() == null) {
                    definedKey.setFKCols(fkCols);
            }
        }
    }
    /** Get an entity by its table name.
     *
     * @param schema schema name
     * @param table table name
     * @return entity name
     */
    private String getEntityByTable(String schema,String table) {
        String entityName;
        if (schema == null) {
            if (db.getSchema() == null) {
                entityName = entityByTableName.get(table);
            } else {
                /* buggy jdbc driver */
                entityName = entityByTableName.get(db.getSchema()+"."+table);
            }
        } else {
            if (db.getSchema() == null) {
                /* means the jdbc driver is using a default schema name,
                 * like PUBLIC for hsqldb (OR that we want an instance in another database connexion. TODO) */
                entityName = entityByTableName.get(schema+"."+table);
                if (entityName == null) {
                    entityName = entityByTableName.get(table);
                }
            } else {
                /* what if not equal? rather strange... */
                if (schema.equals(db.getSchema())) {
                    Logger.error("reverse: was expecting schema '"+db.getSchema()+"' and got schema '"+schema+"'! Please report this error.");
                }
                entityName = entityByTableName.get(schema+"."+table);
            }
        }
        return entityName;
    }
    /** Sort columns in <code>target</code> the same way <code>unordered</code> would have to
     * be sorted to be like <code>ordered</code>.
     * @param ordered ordered list reference
     * @param unordered unordered list reference
     * @param target target list
     * @return sorted target list
     */
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
    /** adapt case
     *
     * @param name name to adapt
     * @return adapted name
     */
    private String adaptCase(String name) {
        return db.adaptCase(name);
    }
    /**
     * rough plural calculation.
     * @param name singular
     * @return plural
     */
    private String getExportedKeyName(String name) {
        return adaptCase(name.endsWith("s")? name+"es" : name+"s");
    }
}
