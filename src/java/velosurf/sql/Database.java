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

import java.io.*;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jdom.input.SAXBuilder;

import velosurf.cache.Cache;
import velosurf.context.RowIterator;
import velosurf.model.Attribute;
import velosurf.model.Entity;
import velosurf.model.Action;
import velosurf.util.Logger;
import velosurf.util.LineWriterOutputStream;
import velosurf.util.Cryptograph;
import velosurf.util.StringLists;
import velosurf.web.HttpQueryTool;

/** This class encapsulates  a connection to the database and contains all the stuff relative to it.
 *
 *  <p>To get a new instance, client classes should call one of the getInstance static methods.</p>
 *
 *  <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 */
public class Database {

    /** builds a new connection
     *
     * @exception SQLException thrown by the database engine
     */
    protected Database() throws SQLException {
    }

    /** builds a new connection
     *
     * @param inUser user name
     * @param inPassword password
     * @param inUrl database url
²     * @exception SQLException thrown by the database engine
     */
    protected Database(String inUser,String inPassword,String inUrl) throws SQLException {
        open(inUser,inPassword,inUrl,null,null);
    }

    /** builds a new connection
     *
     * @param inUser user name
     * @param inPassword password
     * @param inUrl database url
     * @param inDriver driver java class name
     * @exception SQLException thrown by the database engine
     */
    protected Database(String inUser,String inPassword,String inUrl,String inDriver) throws SQLException {
        open(inUser,inPassword,inUrl,inDriver,null);
    }

    /** builds a new connection
     *
     * @param inUser user name
     * @param inPassword password
     * @param inUrl database url
     * @param inDriver driver java class name
     * @param inSchema schema name to use
     * @exception SQLException thrown by the database engine
     */
    protected Database(String inUser,String inPassword,String inUrl,String inDriver,String inSchema) throws SQLException {
        open(inUser,inPassword,inUrl,inDriver,inSchema);
    }

    /** open the connection
     *
     * @param inUser user name
     * @param inPassword password
     * @param inUrl database url
     * @param inDriver driver java class name
     * @exception SQLException thrown by the database engine
     */
    protected void open(String inUser,String inPassword,String inUrl,String inDriver) throws SQLException {
        open(inUser,inPassword,inUrl,inDriver,null);
    }

    /** open the connection
     *
     * @param inUser user name
     * @param inPassword password
     * @param inUrl database url
     * @param inDriver driver java class name
     * @param inSchema schema name
     * @exception SQLException thrown by the database engine
     */
    protected void open(String inUser,String inPassword,String inUrl,String inDriver,String inSchema) throws SQLException {

        mUser = inUser;
        mPassword = inPassword;
        mUrl = inUrl;
        mSchema = inSchema;

        if (mDriverInfo == null) mDriverInfo = loadDriver(mUrl,inDriver);

        connect();
    }

    protected void connect() throws SQLException
    {
        Logger.info("opening database "+mUrl+" for user "+mUser+(mSchema == null?"":" using schema "+mSchema));

        mConnectionPool = new ConnectionPool(mUrl,mUser,mPassword,mSchema,mDriverInfo,true,mMinConnections,mMaxConnections);
        mTransactionConnectionPool = new ConnectionPool(mUrl,mUser,mPassword,mSchema,mDriverInfo,false,1,mMaxConnections);

        mStatementPool = new StatementPool(mConnectionPool);
        mPreparedStatementPool = new PreparedStatementPool(mConnectionPool);

        mTransactionStatementPool = new StatementPool(mTransactionConnectionPool);
        mTransactionPreparedStatementPool = new PreparedStatementPool(mTransactionConnectionPool);
    }

    /** loads the appropriate driver
     *
     * @param inUrl database url
     * @param inDriver driver java class name
     * @return vendor name
     */
    protected static DriverInfo loadDriver(String inUrl,String inDriver) {

        DriverInfo vendor = null;

        if (Logger.getLogLevel() == Logger.TRACE_ID)
        {
            // Initialize log
            //   DriverManager.setLogWriter(Logger.getWriter()); -> doesn't work with jdbc 1.0 drivers
            //   so use the deprecated form
            DriverManager.setLogStream(new PrintStream(new LineWriterOutputStream(Logger.getWriter())));
        }

        // try to deduce the database vendor from the url
        vendor = DriverInfo.getDriverInfo(inUrl);

        if (inDriver!=null) {
            try { Class.forName(inDriver); }
            catch (Exception e) { Logger.log(e); }
        }
        else if (vendor != null) {
            // try to load one of the known drivers
            String[] drivers = vendor.getDrivers();
            for (int i=0;i<drivers.length;i++)
            try {
                Class.forName(drivers[i]);
                break;
            }
            catch (Exception e) { }
        }

        return vendor;
    }

    protected void initCryptograph()
    {
        if (mCryptograph != null) return;
        // to initialize the cryptograph, we need a chunk of user-provided bytes
        // they must be persistent, so that urls that use encrypted params remain valid
        // => use the database url if null
        if (mSeed == null) mSeed = mUrl;
        try {
            mCryptograph = (Cryptograph)Class.forName("velosurf.util.DESCryptograph").getDeclaredConstructor(new Class[] {}).newInstance(new Object[] {});
            mCryptograph.init(mSeed);
        }
        catch(Exception e) {
            Logger.error("Cannot initialize the cryptograph");
            Logger.log(e);
        }
    }

    /** get a unique Database from connection params
     *
     * @param inUser user name
     * @param inPassword password
     * @param inUrl database url
     * @exception SQLException thrown by the database engine
     * @return a new connection
     */
    public static Database getInstance(String inUser,String inPassword,String inUrl) throws SQLException {
        return getInstance(inUser,inPassword,inUrl,null,null);
    }

    /** get a unique Database from connection params
     *
     * @param inUser user name
     * @param inPassword password
     * @param inUrl database url
     * @param inDriver driver java class name
     * @exception SQLException thrown by the database engine
     * @return a new connection
     */
    public static Database getInstance(String inUser,String inPassword,String inUrl,String inDriver) throws SQLException {
        return getInstance(inUser,inPassword,inUrl,inDriver,null);
    }

    /** get a unique Database from connection params
     *
     * @param inUser user name
     * @param inPassword password
     * @param inUrl database url
     * @param inDriver driver java class name
     * @param inSchema schema
     * @exception SQLException thrown by the database engine
     * @return a new connection
     */
    public static Database getInstance(String inUser,String inPassword,String inUrl,String inDriver,String inSchema) throws SQLException {
        Integer hash = Integer.valueOf(inUser.hashCode() ^ inPassword.hashCode() ^ inUrl.hashCode() ^ (inDriver==null?0:inDriver.hashCode()) ^ (inSchema==null?0:inSchema.hashCode()) );
        Database instance = (Database)sConnectionsByParams.get(hash);
        if (instance == null) {
            instance = new Database(inUser,inPassword,inUrl,inDriver,inSchema);
            sConnectionsByParams.put(hash,instance);
        }
        return instance;
    }

    /** get a unique Database from config filename
     *
     * @param inConfigFilename config filename
     * @exception SQLException thrown by the database engine
     * @return a new connection
     */
    public static Database getInstance(String inConfigFilename) throws SQLException,FileNotFoundException,IOException {
        Integer hash = Integer.valueOf(inConfigFilename.hashCode());
        Database instance = (Database)sConnectionsByConfigFile.get(hash);
        if (instance == null) {
            instance = getInstance(new FileInputStream(inConfigFilename));
            sConnectionsByConfigFile.put(hash,instance);
        }
        return instance;
    }

    /** get a new connection
     * @param inConfig config filename
     * @exception SQLException thrown by the database engine
     * @return a new connection
     */
    public static Database getInstance(InputStream inConfig) throws SQLException,IOException {
        Database instance = new Database();
        instance.readConfigFile(inConfig);
        instance.connect();
        instance.readMetaData();
        return instance;
    }

    /** issue a query
     *
     * @param inQuery an SQL query
     * @return the resulting RowIterator
     */
    public RowIterator query(String inQuery) { return query(inQuery,null); }
    /** issue a query, knowing the resulting entity
     *
     * @param inQuery an SQL query
     * @param inEntity the resulting entity
     * @return return the resulting row iterator
     */
    public RowIterator query(String inQuery,Entity inEntity) {
        PooledStatement statement = null;
        try {
            statement=mStatementPool.getStatement();
            return statement.query(inQuery,inEntity);
        }
        catch (SQLException sqle) {
            Logger.log(sqle);
            return null;
        }
    }

    /** evaluate a query to a scalar
     *
     * @param inQuery an sql query
     * @return the resulting scalar
     */
    public Object evaluate(String inQuery) {
        PooledStatement statement = null;
        try {
            statement=mStatementPool.getStatement();
            return statement.evaluate(inQuery);
        }
        catch (SQLException sqle) {
            Logger.log(sqle);
            return null;
        }
    }

/*    public RowIterator query(String inEntity,String inAttribute,List inParams) throws SQLException {

    }*/

    /** prepare a query
     *
     * @param inQuery an sql query
     * @return the pooled prepared statement corresponding to the query
     */
    public PooledPreparedStatement prepare(String inQuery) {
        PooledPreparedStatement statement = null;
        try {
            statement = mPreparedStatementPool.getPreparedStatement(inQuery);
            return statement;
        }
        catch (SQLException sqle) {
            Logger.log(sqle);
            return null;
        }
    }

    /** prepare a query which is part of a transaction
     *
     * @param inQuery an sql query
     * @return the prepared statemenet corresponding to the query
     */
    public PooledPreparedStatement transactionPrepare(String inQuery) {
        PooledPreparedStatement statement = null;
        try {
            statement = mTransactionPreparedStatementPool.getPreparedStatement(inQuery);
            return statement;
        }
        catch (SQLException sqle) {
            Logger.log(sqle);
            return null;
        }
    }

    /** issues an update query
     *
     * @param inQuery an sql query
     * @return the number of affected rows
     */
    public int update(String inQuery) {
        try {
               PooledStatement statement = mStatementPool.getStatement();
            return statement.update(inQuery);
        }
        catch (SQLException sqle) {
            setError(sqle.getMessage());
            Logger.log(sqle);
            return -1;
        }
    }

    /** issue an update query that is part of a transaction
     *
     * @param inQuery an sql query
     * @return the number of affected rows
     */
    public int transactionUpdate(String inQuery) {
        try {
            PooledStatement statement = mTransactionStatementPool.getStatement();
            return statement.update(inQuery);
        }
        catch (SQLException sqle) {
            setError(sqle.getMessage());
            Logger.log(sqle);
            return -1;
        }
    }

    /** close the connection
     *
     * @exception SQLException thrown by the database engine
     */
    public void close() throws SQLException {
        mConnectionPool.clear();
        mConnectionPool = null;
        mTransactionConnectionPool.clear();
        mTransactionConnectionPool = null;
        mStatementPool.clear();
        mStatementPool = null;
        mTransactionStatementPool.clear();
        mTransactionStatementPool = null;
        mPreparedStatementPool.clear();
        mPreparedStatementPool = null;
        mTransactionPreparedStatementPool.clear();
        mTransactionPreparedStatementPool = null;
    }

    /** display statistics about the statements pools
     */
    public void displayStats() {
        System.out.println("DB statistics:");
        int [] normalStats = mStatementPool.getUsageStats();
        int [] preparedStats = mPreparedStatementPool.getUsageStats();
        System.out.println("\tsimple statements   - "+normalStats[0]+" free statements out of "+normalStats[1]);
        System.out.println("\tprepared statements - "+preparedStats[0]+" free statements out of "+preparedStats[1]);
    }

    /** get a jdbc connection
     *
     * @return jdbc connection
     */
    public Connection getConnection() throws SQLException {
        return mConnectionPool.getConnection();
    }

    /** get the underlying jdbc connection used for transactions
     *
     * @return jdbc connection
     */
    public Connection getTransactionConnection() throws SQLException {
        return mTransactionConnectionPool.getConnection();
    }

    /** read the meta data from the database : reverse engeenering
     *
     * @exception SQLException thrown by the database engine
     */
    protected void readMetaData() throws SQLException {

        DatabaseMetaData meta = getConnection().getMetaData();
        ResultSet tables = null;

        // perform the reverse enginering
        try    {

            switch(mReverseMode)
            {
                case REVERSE_FULL:
                    tables = meta.getTables(null,mSchema,null,null);
                    while (tables.next()) {
                        String tableName = adaptCase(tables.getString("TABLE_NAME"));
                        if (tableName.indexOf('/')!=-1) continue; // skip special tables (Oracle)
                        Entity entity = (Entity)mEntitiesByTableName.get(tableName);
                        if (entity == null) entity = getEntityCreate(adaptCase(tableName));
                        else mEntitiesByTableName.remove(tableName);
                        mEntities.put(tableName,entity);
                        readTableMetaData(meta,entity,tableName);
                    }
                    for(Iterator e = mEntitiesByTableName.keySet().iterator();e.hasNext();)
                    {
                        Logger.warn("table '"+(String)e.next()+"' not found!");
                    }
                    break;
                case REVERSE_PARTIAL:
                    for(Iterator e = mEntities.keySet().iterator();e.hasNext();)
                    {
                        Entity entity = (Entity)mEntities.get(e.next());
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

    protected void readTableMetaData(DatabaseMetaData meta,Entity entity,String tableName) throws SQLException {

        List keylist = StringLists.getPopulatedArrayList(10); // ever seen a primary key with more than 10 columns ?!

        // read columns
        ResultSet cols = null;
        try    {
            cols = meta.getColumns(null,mSchema,tableName,null);
            while (cols.next()) {
                String column = adaptCase(cols.getString("COLUMN_NAME"));
                entity.addColumn(column);
            }
        }
        finally {
            if (cols != null) cols.close();
        }

        // read primary keys
        ResultSet pks = null;
        try    {
            pks = meta.getPrimaryKeys(null,mSchema,tableName);
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
            entity.reverseEnginered();
        }
    }

    /** read the given config file
     *
     * @param inConfigFile config file pathname
     * @exception SQLException thrown by the database engine
     */
    public void readConfigFile(String inConfigFile) throws SQLException,IOException {
        try {
            Logger.info("reading properties from file "+inConfigFile+"...");
            readConfigFile(new FileInputStream(inConfigFile));
        }
        catch (FileNotFoundException fnfe) {
            Logger.log(fnfe);
        }
    }

    /** read configuration from the given input stream
     *
     * @param inConfig input stream on the config file
     * @exception SQLException thrown by the database engine
     */
    public void readConfigFile(InputStream inConfig) throws SQLException,IOException {
        try {
            Logger.info("reading properties...");

            // build JDOM tree
            Document document = new SAXBuilder().build(inConfig);
            Element database = document.getRootElement();

            String loglevel = database.getAttributeValue("loglevel");
            if (checkSyntax("loglevel",loglevel,new String[]{"trace","debug","info","warn","error"})) {
                if ("trace".equalsIgnoreCase(loglevel)) Logger.setLogLevel(Logger.TRACE_ID);
                else if ("debug".equalsIgnoreCase(loglevel)) Logger.setLogLevel(Logger.DEBUG_ID);
                else if ("info".equalsIgnoreCase(loglevel)) Logger.setLogLevel(Logger.INFO_ID);
                else if ("warn".equalsIgnoreCase(loglevel)) Logger.setLogLevel(Logger.WARN_ID);
                else if ("error".equalsIgnoreCase(loglevel)) Logger.setLogLevel(Logger.ERROR_ID);
            }

            String access = database.getAttributeValue("default-access");
            if (checkSyntax("access",access,new String[]{"ro","rw"}))
                mDefaultReadOnly = !access.equalsIgnoreCase("rw");

            String caching = database.getAttributeValue("default-caching");
            if (checkSyntax("caching",caching,new String[] {"none","no","yes","soft","full"})) {
                mDefaultCaching = parseCaching(caching);
                if (mDefaultCaching == Cache.FULL_CACHE) Logger.warn("The full caching method is deprecated and will be removed in future versions.");
            }

            String reverseMode = database.getAttributeValue("reverse");
            if (checkSyntax("reverse",reverseMode,new String[]{"none","partial","full"})) {
                if ("full".equalsIgnoreCase(reverseMode) || reverseMode == null) mReverseMode = REVERSE_FULL;
                else if ("partial".equalsIgnoreCase(reverseMode)) mReverseMode = REVERSE_PARTIAL;
                else if ("none".equalsIgnoreCase(reverseMode)) mReverseMode = REVERSE_NONE;
            }

            mUser = database.getAttributeValue("user");
            mPassword = database.getAttributeValue("password");
            mUrl = database.getAttributeValue("url");
            String driver = database.getAttributeValue("driver");

            String minstr = database.getAttributeValue("min-connections");
            if (minstr != null) {
                try {
                    int min = Integer.parseInt(minstr);
                    if (min>0) mMinConnections = min;
                    else Logger.error("error: the parameter 'min-connections' wants an integer > 0 !");
                }
                catch(NumberFormatException nfe) {
                    Logger.error("error: the parameter 'min-connections' wants an integer!");
                }
            }

            String maxstr = database.getAttributeValue("max-connections");
            if (maxstr != null) {
                try {
                    int max = Integer.parseInt(maxstr);
                    if (max>=mMinConnections) mMaxConnections = max;
                    else Logger.error("error: the parameter 'max-connections' must be >= min-connection!");
                }
                catch(NumberFormatException nfe) {
                    Logger.error("error: the parameter 'max-connections' wants an integer!");
                }
            }

            mSeed = database.getAttributeValue("seed");

            mDriverInfo = loadDriver(mUrl,driver);

            String caseSensivity = database.getAttributeValue("case");
            // if case-sensivity has not been set explicitely, deduce it from the driver
            if (caseSensivity == null) caseSensivity = mDriverInfo.getCaseSensivity();

            if (checkSyntax("case-sensivity",caseSensivity,new String[]{"sensitive","uppercase","lowercase"})) {
                Logger.info("Case-sensivity: "+caseSensivity);
                if("sensitive".equalsIgnoreCase(caseSensivity)) mCaseSensivity = CASE_SENSITIVE;
                else if("uppercase".equalsIgnoreCase(caseSensivity)) mCaseSensivity = UPPERCASE;
                else if ("lowercase".equalsIgnoreCase(caseSensivity)) mCaseSensivity = LOWERCASE;
            }

            mSchema = adaptCase(database.getAttributeValue("schema"));

            mEntities = new HashMap();
            mRootEntity = new Entity(this,"db",false,Cache.NO_CACHE);
            mEntities.put("db",mRootEntity);
            mEntitiesByTableName = new HashMap();

            // define root attributes
            for (Iterator rootAttributes = database.getChildren("attribute").iterator();rootAttributes.hasNext();) {
                mRootEntity.defineAttribute((Element)rootAttributes.next());
            }

            // define root actions
            for(Iterator rootActions = database.getChildren("action").iterator();rootActions.hasNext();) {
                mRootEntity.defineAction((Element)rootActions.next());
            }

            // define entities
            for (Iterator entities = database.getChildren("entity").iterator();entities.hasNext();) {
                Element entityElement = (Element)entities.next();
                String entityName = entityElement.getAttributeValue("name");
                String table = adaptCase(entityElement.getAttributeValue("table"));
                Entity entity = getEntityCreate(adaptCase(entityName));
                mEntities.put(adaptCase(entityName),entity);
                if (table != null) entity.setTableName(adaptCase(table));
                mEntitiesByTableName.put(entity.getTableName(),entity);

                // custom class
                String cls = entityElement.getAttributeValue("class");
                // TODO : try to instanciate once to avoid subsequent errors
                if (cls != null) entity.setInstanceClass(cls);

                // access
                access = entityElement.getAttributeValue("access");
                if (checkSyntax(entityName+".access",access,new String[]{"ro","rw"})) {
                    access = access.toLowerCase();
                    if (access.equalsIgnoreCase("ro")) entity.setReadOnly(true);
                    else if (access.equalsIgnoreCase("rw")) entity.setReadOnly(false);
                }

                // caching
                caching = entityElement.getAttributeValue("caching");
                if (checkSyntax("caching",caching,new String[] {"none","no","yes","soft","full"}))
                    entity.setCachingMethod(parseCaching(caching));

                // obfuscation
                String obfuscate = entityElement.getAttributeValue("obfuscate");
                boolean needObfuscator = false;
                if (obfuscate != null) {
                    needObfuscator = true;
                    List obfuscatedCols = new ArrayList();
                    StringTokenizer tokenizer = new StringTokenizer(obfuscate,", ");
                    while(tokenizer.hasMoreTokens()) {
                        obfuscatedCols.add(adaptCase(tokenizer.nextToken()));
                    }
                    entity.setObfuscated(obfuscatedCols);
                }

                if (needObfuscator) initCryptograph();

                String localize = entityElement.getAttributeValue("obfuscate");
                if (localize != null) {
                    List localizedCols = new ArrayList();
                    StringTokenizer tokenizer = new StringTokenizer(localize,", ");
                    while(tokenizer.hasMoreTokens()) {
                        localizedCols.add(adaptCase(tokenizer.nextToken()));
                    }
                    entity.setLocalized(localizedCols);
                }

                // localization

                // define entity attributes
                for (Iterator attributes = entityElement.getChildren("attribute").iterator();attributes.hasNext();) {
                    entity.defineAttribute((Element)attributes.next());
                }

                // define entity actions
                for(Iterator actions = entityElement.getChildren("action").iterator();actions.hasNext();) {
                    entity.defineAction((Element)actions.next());
                }

                // autofetching
                String autofetch = entityElement.getAttributeValue("autofetch");
                if (autofetch != null) {
                    String target = entityName;
                    String param = autofetch;
                    boolean protect = false;
                    int n = autofetch.indexOf('=');
                    if (n != -1) {
                        target = autofetch.substring(0,n).trim();
                        param = autofetch.substring(n+1).trim();
                        if (target.startsWith("query.")) {
                            target = target.substring(6);
                            protect = true;
                        }
                    }
                    HttpQueryTool.autofetch(entity,param,target,protect);
                }
            }

            if (mSchema != null) {
                // share entities
                sSharedCatalog.put(getMagicNumber(mSchema),mEntities);
            }

            Logger.info("Config file successfully read.");

            // startup action
            Action startup = mRootEntity.getAction("startup");
            if (startup != null) startup.perform(null);
        }
        catch (JDOMException jdome) {
            Logger.log(jdome);
        }
    }

    /** changes to lowercase or uppercase if needed
     *
     * @param identifier
     * @return changed identifier
     */
    public String adaptCase(String identifier) {
        if (identifier == null) return null;
        switch(mCaseSensivity) {
            case CASE_SENSITIVE: return identifier;
            case UPPERCASE: return identifier.toUpperCase();
            case LOWERCASE: return identifier.toLowerCase();
            default:
                Logger.error("bad case-sensivity!");
                return identifier;
        }
    }

    /** parse a caching value
     *
     * @param caching string describing the type of caching
     * @return type of caching
     */
    private static int parseCaching(String caching) {
        return
            caching == null || caching.equalsIgnoreCase("none") || caching.equalsIgnoreCase("no") ? Cache.NO_CACHE :
            caching.equalsIgnoreCase("soft") || caching.equalsIgnoreCase("yes") ? Cache.SOFT_CACHE :
            caching.equalsIgnoreCase("full") ? Cache.FULL_CACHE :
            Cache.NO_CACHE;
    }

    /** check the syntax of a parameter in the config file
     *
     * @param inParamName name of the parameter
     * @param inParamValue value of the parameter
     * @param inPossibleValues possible values for the parameter
     * @return whether the syntax is correct
     */
    protected boolean checkSyntax(String inParamName, String inParamValue, String[] inPossibleValues) {
        if (inParamValue == null) return false;
        List possible = Arrays.asList(inPossibleValues);
        if (inParamValue!=null && Arrays.asList(inPossibleValues).contains(inParamValue.toLowerCase()))
            return true;
        else {
            Logger.error("Parameter '"+inParamName+"' wants one of: " + StringLists.join(possible,","));
            return false;
        }
    }


    /** get a named entity or creeate it if it doesn't exist
     *
     * @param name name of an entity
     * @return the named entity
     */
    public Entity getEntityCreate(String name) {
        Entity entity = getEntity(name);
        if (entity == null) {
            Logger.trace("Created entity: "+name);
            entity = new Entity(this,name,mDefaultReadOnly,mDefaultCaching);
            mEntities.put(adaptCase(name),entity);
        }
        return entity;
    }

    /** get an existing entity
     *
     * @param inName the name of an entity
     * @return the named entity
     */
    public Entity getEntity(String inName) {
        int i;
        Entity entity=(Entity)mEntities.get(adaptCase(inName));
        if (entity == null && inName != null && (i=inName.indexOf('.')) != -1) {
            // imported from another schema ?
            String schema = inName.substring(0,i);
            inName = inName.substring(i+1);
            Map external = (Map)sSharedCatalog.get(getMagicNumber(schema));
            if (external != null) entity = (Entity)external.get(inName);
        }
        return entity;
    }

    /** get a named attribute
     *
     * @param inName name of an attribute
     * @return the named attribute
     */
    public Attribute getAttribute(String inName) {
        if (mRootEntity != null) return mRootEntity.getAttribute(adaptCase(inName));
        else return null;
    }

    /** get a named action
     *
     * @param inName name of an attribute
     * @return the named attribute
     */
    public Action getAction(String inName) {
        if (mRootEntity != null) return mRootEntity.getAction(adaptCase(inName));
        else return null;
    }

    /** set the error string
     *
     * @param inError error string
     */
    public void setError(String inError) {
        mError = inError;
    }

    /** get the error string
     *
     * @return error string
     */
    public String getError() {
        return mError;
    }

    /** obfuscate the given value
     * @param value value to obfuscate
     *
     * @return obfuscated value
     */
    public String obfuscate(Object value)
    {
        if (value == null) return null;
        String encoded = mCryptograph.encrypt(value.toString());

        // we want to avoid some characters fot HTTP GET
        encoded = encoded.replace('=','.');
        encoded = encoded.replace('/','_');
        encoded = encoded.replace('+','*');

        return encoded;
    }

    /** de-obfuscate the given value
     * @param value value to de-obfuscate
     *
     * @return obfuscated value
     */
    public String deobfuscate(Object value)
    {
        if (value == null) return null;

        String ret = value.toString();

        // recover exact encoded string
        ret = ret.replace('.','=');
        ret = ret.replace('_','/');
        ret = ret.replace('*','+');

        ret = mCryptograph.decrypt(ret);

        if (ret == null) {
            Logger.error("deobfuscation of value '"+value+"' failed!");
            return null;
        }

        return ret;
    }

    /** get database vendor
     * @return the database vendor
     */
    public DriverInfo getDriverInfo() {
        return mDriverInfo;
    }

    /** get database case-sensivity
     *
     * @return case-sensivity
     */
    public int getCaseSensivity() {
        return mCaseSensivity;
    }

    /** get the integer key used to share schema entities among instances
     */
    private Integer getMagicNumber(String schema) {
        // url is not checked for now because for some databases, the schema is part of the url.
        return Integer.valueOf((mUser/*+mUrl*/+schema).hashCode());
    }

    /** get the schema
     * @return the schema
     */
    public String getSchema() {
        return mSchema;
    }

    /** database user
     */
    protected String mUser = null;
    /** database user's password
     */
    protected String mPassword = null;
    /** database url
     */
    protected String mUrl = null;
    /** schema
     */
    protected String mSchema = null;

    /**
     * Pool of connections
     */
    protected ConnectionPool mConnectionPool = null;
    protected int mMinConnections = 1; // applies to mConnectionPool (min connections is always 1 for mTransactionConnectionPool)
    protected int mMaxConnections = 50; // applies to mConnectionPool and mTransactionConnectionPool

    /**
     * Pool of connections for transactions
     */
    protected ConnectionPool mTransactionConnectionPool = null;

    /** pool of statements
     */
    protected StatementPool mStatementPool = null;

    /** pool of statements for transactions
     */
    protected StatementPool mTransactionStatementPool = null;

    /** pool of prepared statements
     */
    protected PreparedStatementPool mPreparedStatementPool = null;

    /** pool of prepared statements for transactions
     */
    protected PreparedStatementPool mTransactionPreparedStatementPool = null;

    /** default access mode
     */
    protected boolean mDefaultReadOnly = true;
    /** default caching mode
     */
    protected int mDefaultCaching = Cache.NO_CACHE;

    /** error string for the last error
     */
    protected String mError = "no error";

    /** map name->entity
     */
    protected Map mEntities = null; // entity name -> entity;

    /* map table->entity, valid only between readConfigFile and readMetaData
    */
    protected Map mEntitiesByTableName = null;

    /** root entity that contains all root attributes and actions
     */
    protected Entity mRootEntity = null;

    /** driver infos (database vendor specific)
     */
    protected DriverInfo mDriverInfo = null;

    /** random seed used to initialize the cryptograph
     */
    private String mSeed = null;

    /** cryptograph used to encrypt/decrypt database ids
     */
    private Cryptograph mCryptograph = null;

    /** case-sensitive policy */
    public static final int CASE_SENSITIVE = 1;
    public static final int UPPERCASE = 2;
    public static final int LOWERCASE = 3;

    /** case-sensivity */
    protected int mCaseSensivity = CASE_SENSITIVE;

    /** reverse-enginering modes */
    public static final int REVERSE_NONE = 1;
    public static final int REVERSE_PARTIAL = 2;
    public static final int REVERSE_FULL = 3;

    /* reverse-enginering mode */
    protected int mReverseMode = REVERSE_FULL;

    /** map parameters -> instances */
    private static Map sConnectionsByParams = new HashMap();

    /** map config files -> instances */
    private static Map sConnectionsByConfigFile = new HashMap();

    /** Shared catalog, to share entities among instances.
     * <br>
     * Key is hashcode of (name+password+url+schema), value is an entities map.
     */
    private static Map sSharedCatalog = new HashMap();
}
