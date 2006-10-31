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
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

import velosurf.cache.Cache;
import velosurf.context.RowIterator;
import velosurf.model.Attribute;
import velosurf.model.Entity;
import velosurf.model.Action;
import velosurf.util.Logger;
import velosurf.util.LineWriterOutputStream;
import velosurf.util.Cryptograph;
import velosurf.util.XIncludeResolver;

/** This class encapsulates  a connection to the database and contains all the stuff relative to it.
 *
 *  <p>To get a new instance, client classes should call one of the getInstance static methods.</p>
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 *
 */
public class Database {

    /** builds a new connection
     *
     */
    private Database() {
    }

    /** builds a new connection
     *
     * @param inUser user name
     * @param inPassword password
     * @param inUrl database url
     * @exception SQLException thrown by the database engine
     */
    public Database(String inUser,String inPassword,String inUrl) throws SQLException {
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
    public Database(String inUser,String inPassword,String inUrl,String inDriver) throws SQLException {
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
    public Database(String inUser,String inPassword,String inUrl,String inDriver,String inSchema) throws SQLException {
        open(inUser,inPassword,inUrl,inDriver,inSchema);
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
            String base = null;
            inConfigFilename = inConfigFilename.replace('\\','/');
            int i = inConfigFilename.lastIndexOf('/');
            if (i == -1) {
                base = ".";
            } else {
                base = inConfigFilename.substring(0,i);
            }
            instance = getInstance(new FileInputStream(inConfigFilename),new XIncludeResolver(base));
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
        return Database.getInstance(inConfig,null);
    }

    /** get a new connection
     * @param inConfig config filename
     * @exception SQLException thrown by the database engine
     * @return a new connection
     */
    public static Database getInstance(InputStream inConfig,XIncludeResolver xincludeResolver) throws SQLException,IOException {
        Database instance = new Database();
        instance.readConfigFile(inConfig,xincludeResolver);
        instance.connect();
        instance.getReverseEngineer().readMetaData();
        return instance;
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
        mDriverClass = inDriver;
        connect();
    }

    protected void connect() throws SQLException
    {
        Logger.info("opening database "+mUrl+" for user "+mUser+(mSchema == null?"":" using schema "+mSchema));

        loadDriver();

        mConnectionPool = new ConnectionPool(mUrl,mUser,mPassword,mSchema,mDriverInfo,true,mMinConnections,mMaxConnections);
        mTransactionConnectionPool = new ConnectionPool(mUrl,mUser,mPassword,mSchema,mDriverInfo,false,1,mMaxConnections);

        mStatementPool = new StatementPool(mConnectionPool);
        mPreparedStatementPool = new PreparedStatementPool(mConnectionPool);

        mTransactionStatementPool = new StatementPool(mTransactionConnectionPool);
        mTransactionPreparedStatementPool = new PreparedStatementPool(mTransactionConnectionPool);

        // startup action
        Action startup = mRootEntity.getAction("startup");
        if (startup != null) startup.perform(null);
    }

    protected void setReadOnly(boolean readOnly) {
        mReadOnly = readOnly;
    }

    protected void setCaching(int cachingMethod) {
        mCaching = cachingMethod;
    }

    public void setUser(String user) {
        mUser = user;
    }

    public void setPassword(String password) {
       mPassword = password;
    }

    public void setURL(String url) {
        mUrl = url;
    }

    public void setDriver(String driverClass) {
        mDriverClass = driverClass;
    }

    public void setSchema(String schema) {
        mSchema = schema;
        if(mSchema != null) {
            // share entities
            sSharedCatalog.put(getMagicNumber(mSchema),mEntities);
        }
    }

    public void setMinConnections(int minConnections) {
        mMinConnections = minConnections;
    }

    public void setMaxConnections(int maxConnections) {
        mMaxConnections = maxConnections;
    }

    public void setSeed(String seed) {
        mSeed = seed;
    }

    public void setCase(int caseSensivity) {
        mCaseSensivity = caseSensivity;
    }

    /** loads the appropriate driver
     *
     */
    protected @SuppressWarnings("deprecation") void loadDriver() {

        if (mDriverLoaded) return;
        if (Logger.getLogLevel() == Logger.TRACE_ID)
        {
            /* Initialize log
             *   DriverManager.setLogWriter(Logger.getWriter()); -> doesn't work with jdbc 1.0 drivers
             *   so use the deprecated form
             *  TODO: detect driver jdbc conformance
             */
            if(Logger.getLogLevel() <= Logger.DEBUG_ID) {
                DriverManager.setLogStream(new PrintStream(new LineWriterOutputStream(Logger.getWriter())));
            }
        }

        /* driver behaviour */
        mDriverInfo = DriverInfo.getDriverInfo(mUrl,mDriverClass);

        mReverseEngineer.setDriverInfo(mDriverInfo);

        if (mDriverClass!=null) {
            try {
                Class.forName(mDriverClass);
                mDriverLoaded = true;
            }
            catch (Exception e) { Logger.log(e); }
        }
        else if (mDriverInfo != null) {
            // try to load one of the known drivers
            String[] drivers = mDriverInfo.getDrivers();
            for (int i=0;i<drivers.length;i++)
            try {
                Class.forName(drivers[i]);
                mDriverLoaded = true;
                break;
            }
            catch (Exception e) { }
        }
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

    public ReverseEngineer getReverseEngineer() {
        return mReverseEngineer;
    }

    /** issue a query
     *
     * @param inQuery an SQL query
     * @return the resulting RowIterator
     */
    public RowIterator query(String inQuery) throws SQLException {
        return query(inQuery,null);
    }

    /** issue a query, knowing the resulting entity
     *
     * @param inQuery an SQL query
     * @param inEntity the resulting entity
     * @return return the resulting row iterator
     */
    public RowIterator query(String inQuery,Entity inEntity) throws SQLException {
        PooledStatement statement = null;
        statement=mStatementPool.getStatement();
        return statement.query(inQuery,inEntity);
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
     * @return a jdbc connection wrapper (which extends java.sql.Connection)
     */
    public ConnectionWrapper getConnection() throws SQLException {
        return mConnectionPool.getConnection();
    }

    /** get the underlying jdbc connection used for transactions, and mark it right away as busy
     *
     * @return a jdbc connection wrapper (which extends java.sql.Connection)
     */
    public synchronized ConnectionWrapper getTransactionConnection() throws SQLException {
        ConnectionWrapper ret = mTransactionConnectionPool.getConnection();
        ret.enterBusyState();
        return ret;
    }

    /** read configuration from the given input stream
     *
     * @param inConfig input stream on the config file
     * @exception SQLException thrown by the database engine
     */
    private void readConfigFile(InputStream inConfig,XIncludeResolver xincludeResolver) throws SQLException,IOException {
        try {
            new ConfigLoader(this,xincludeResolver).loadConfig(inConfig);

        } catch (Exception e) {
            Logger.error("could not load configuration!");
            Logger.log(e);
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

    /** add a new entity
     *
     */
    public void addEntity(Entity entity) {
        String name = entity.getName();
        Entity previous = mEntities.put(adaptCase(name),entity);
        if (previous != null) {
            Logger.warn("replacing an existing entity with a new one ("+name+")");
        }
        if(name.equals("velosurf.root")) {
            /* this is the root entity */
            mRootEntity = entity;
        }
    }

    public Entity getRootEntity() {
        return mRootEntity;
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
            entity = new Entity(this,name,mReadOnly,mCaching);
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

    public Map<String,Entity> getEntities() {
        return mEntities;
    }

    /** get a named attribute
     *
     * @param inName name of an attribute
     * @return the named attribute
     */
    public Attribute getAttribute(String inName) {
        return mRootEntity.getAttribute(adaptCase(inName));
    }

    /** get a named action
     *
     * @param inName name of an attribute
     * @return the named attribute
     */
    public Action getAction(String inName) {
        return mRootEntity.getAction(adaptCase(inName));
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

    /** whether the JDBC driver has been loaded */
    protected boolean mDriverLoaded = false;

    /** driver class name, if provided in the config file
     */
    protected String mDriverClass = null;

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
    protected boolean mReadOnly = true;
    /** default caching mode
     */
    protected int mCaching = Cache.NO_CACHE;

    /** map name->entity
     */
    protected Map<String,Entity> mEntities = new HashMap<String,Entity>();

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
    public static final int CASE_UNKNOWN = 0;
    public static final int CASE_SENSITIVE = 1;
    public static final int UPPERCASE = 2;
    public static final int LOWERCASE = 3;

    /** case-sensivity */
    protected int mCaseSensivity = CASE_UNKNOWN;

    /** case-sensivity for context
     */
    private static int sContextCase = LOWERCASE;

    /* context case implemented as a system property for now...
     *TODO: check also other configuration realms or use model.xml
     */
    static {
        String contextCase = System.getProperty("velosurf.case");
        if (contextCase != null) {
            if ("uppercase".equals(contextCase)) {
                sContextCase = UPPERCASE;
            } else if ("lowercase".equals(contextCase)) {
                sContextCase = LOWERCASE;
            } else {
                Logger.error("system property 'velosurf.case' should be 'lowercase' or 'uppercase'");
            }
        }
    }

    public static String adaptContextCase(String str) {
        if(str == null) {
            return null;
        }
        switch(sContextCase) {
            case LOWERCASE:
                return str.toLowerCase();
            case UPPERCASE:
                return str.toUpperCase();
            default:
                Logger.error("unknown context case policy!");
                return str;
        }
    }

    /** map parameters -> instances */
    private static Map sConnectionsByParams = new HashMap();

    /** map config files -> instances */
    private static Map sConnectionsByConfigFile = new HashMap();

    /** Shared catalog, to share entities among instances.
     * <br>
     * Key is hashcode of (name+password+url+schema), value is an entities map.
     */
    private static Map sSharedCatalog = new HashMap();

    protected ReverseEngineer mReverseEngineer = new ReverseEngineer(this);

}
