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
     * @param user user name
     * @param password password
     * @param url database url
     * @exception SQLException thrown by the database engine
     */
    private Database(String user,String password,String url) throws SQLException {
        open(user,password,url,null,null);
    }

    /** builds a new connection
     *
     * @param user user name
     * @param password password
     * @param url database url
     * @param driver driver java class name
     * @exception SQLException thrown by the database engine
     */
    private Database(String user,String password,String url,String driver) throws SQLException {
        open(user,password,url,driver,null);
    }

    /** builds a new connection
     *
     * @param user user name
     * @param password password
     * @param url database url
     * @param driver driver java class name
     * @param schema schema name to use
     * @exception SQLException thrown by the database engine
     */
    private Database(String user,String password,String url,String driver,String schema) throws SQLException {
        open(user,password,url,driver,schema);
    }

    /** get a unique Database from connection params
     *
     * @param user user name
     * @param password password
     * @param url database url
     * @exception SQLException thrown by the database engine
     * @return a new connection
     */
    public static Database getInstance(String user,String password,String url) throws SQLException {
        return getInstance(user,password,url,null,null);
    }

    /** get a unique Database from connection params
     *
     * @param user user name
     * @param password password
     * @param url database url
     * @param driver driver java class name
     * @exception SQLException thrown by the database engine
     * @return a new connection
     */
    public static Database getInstance(String user,String password,String url,String driver) throws SQLException {
        return getInstance(user,password,url,driver,null);
    }

    /** get a unique Database from connection params
     *
     * @param user user name
     * @param password password
     * @param url database url
     * @param driver driver java class name
     * @param schema schema
     * @exception SQLException thrown by the database engine
     * @return a new connection
     */
    public static Database getInstance(String user,String password,String url,String driver,String schema) throws SQLException {
        Integer hash = Integer.valueOf(user.hashCode() ^ password.hashCode() ^ url.hashCode() ^ (driver==null?0:driver.hashCode()) ^ (schema==null?0:schema.hashCode()) );
        Database instance = (Database)connectionsByParams.get(hash);
        if (instance == null) {
            instance = new Database(user,password,url,driver,schema);
            connectionsByParams.put(hash,instance);
        }
        return instance;
    }

    /** get a unique Database from config filename
     *
     * @param configFilename config filename
     * @exception SQLException thrown by the database engine
     * @return a new connection
     */
    public static Database getInstance(String configFilename) throws SQLException,FileNotFoundException,IOException {
        Integer hash = Integer.valueOf(configFilename.hashCode());
        Database instance = (Database)connectionsByConfigFile.get(hash);
        if (instance == null) {
            String base = null;
            configFilename = configFilename.replace('\\','/');
            int i = configFilename.lastIndexOf('/');
            if (i == -1) {
                base = ".";
            } else {
                base = configFilename.substring(0,i);
            }
            instance = getInstance(new FileInputStream(configFilename),new XIncludeResolver(base));
            connectionsByConfigFile.put(hash,instance);
        }
        return instance;
    }

    /** get a new connection
     * @param config config filename
     * @exception SQLException thrown by the database engine
     * @return a new connection
     */
    public static Database getInstance(InputStream config) throws SQLException,IOException {
        return Database.getInstance(config,null);
    }

    /** get a new connection
     * @param config config filename
     * @exception SQLException thrown by the database engine
     * @return a new connection
     */
    public static Database getInstance(InputStream config,XIncludeResolver xincludeResolver) throws SQLException,IOException {
        Database instance = new Database();
        instance.readConfigFile(config,xincludeResolver);
        instance.connect();
        instance.getReverseEngineer().readMetaData();
        return instance;
    }

    /** open the connection
     *
     * @param user user name
     * @param password password
     * @param url database url
     * @param driver driver java class name
     * @exception SQLException thrown by the database engine
     */
    protected void open(String user,String password,String url,String driver) throws SQLException {
        open(user,password,url,driver,null);
    }

    /** open the connection
     *
     * @param user user name
     * @param password password
     * @param url database url
     * @param driver driver java class name
     * @param schema schema name
     * @exception SQLException thrown by the database engine
     */
    protected void open(String user,String password,String url,String driver,String schema) throws SQLException {

        this.user = user;
        this.password = password;
        this.url = url;
        this.schema = schema;
        driverClass = driver;
        connect();
    }

    protected void connect() throws SQLException
    {
        Logger.info("opening database "+url+" for user "+user+(schema == null?"":" using schema "+schema));

        loadDriver();

        connectionPool = new ConnectionPool(url,user,password,schema,driverInfo,true,minConnections,maxConnections);
        transactionConnectionPool = new ConnectionPool(url,user,password,schema,driverInfo,false,1,maxConnections);

        statementPool = new StatementPool(connectionPool);
        preparedStatementPool = new PreparedStatementPool(connectionPool);

        transactionStatementPool = new StatementPool(transactionConnectionPool);
        transactionPreparedStatementPool = new PreparedStatementPool(transactionConnectionPool);

        // startup action
        Action startup = rootEntity.getAction("startup");
        if (startup != null) startup.perform(null);
    }

    protected void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    protected void setCaching(int cachingMethod) {
        caching = cachingMethod;
    }

    public void setUser(String user) {
        this.user = user;
    }

    public void setPassword(String password) {
       this.password = password;
    }

    public void setURL(String url) {
        this.url = url;
    }

    public void setDriver(String driverClass) {
        this.driverClass = driverClass;
    }

    public void setSchema(String schema) {
        this.schema = schema;
        if(this.schema != null) {
            // share entities
            sharedCatalog.put(getMagicNumber(this.schema),entities);
        }
    }

    public void setMinConnections(int minConnections) {
        this.minConnections = minConnections;
    }

    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }

    public void setSeed(String seed) {
        this.seed = seed;
    }

    public void setCase(int caseSensivity) {
        this.caseSensivity = caseSensivity;
    }

    /** loads the appropriate driver
     *
     */
    protected @SuppressWarnings("deprecation") void loadDriver() {

        if (driverLoaded) return;
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
        driverInfo = DriverInfo.getDriverInfo(url,driverClass);

        reverseEngineer.setDriverInfo(driverInfo);

        if (driverClass!=null) {
            try {
                Class.forName(driverClass);
                driverLoaded = true;
            }
            catch (Exception e) { Logger.log(e); }
        }
        else if (driverInfo != null) {
            // try to load one of the known drivers
            String[] drivers = driverInfo.getDrivers();
            for (int i=0;i<drivers.length;i++)
            try {
                Class.forName(drivers[i]);
                driverLoaded = true;
                break;
            }
            catch (Exception e) { }
        }
    }

    protected void initCryptograph()
    {
        if (cryptograph != null) return;
        // to initialize the cryptograph, we need a chunk of user-provided bytes
        // they must be persistent, so that urls that use encrypted params remain valid
        // => use the database url if null
        if (seed == null) seed = url;
        try {
            cryptograph = (Cryptograph)Class.forName("velosurf.util.DESCryptograph").getDeclaredConstructor(new Class[] {}).newInstance(new Object[] {});
            cryptograph.init(seed);
        }
        catch(Exception e) {
            Logger.error("Cannot initialize the cryptograph");
            Logger.log(e);
        }
    }

    public ReverseEngineer getReverseEngineer() {
        return reverseEngineer;
    }

    /** issue a query
     *
     * @param query an SQL query
     * @return the resulting RowIterator
     */
    public RowIterator query(String query) throws SQLException {
        return query(query,null);
    }

    /** issue a query, knowing the resulting entity
     *
     * @param query an SQL query
     * @param entity the resulting entity
     * @return return the resulting row iterator
     */
    public RowIterator query(String query,Entity entity) throws SQLException {
        PooledStatement statement = null;
        statement=statementPool.getStatement();
        return statement.query(query,entity);
    }

    /** evaluate a query to a scalar
     *
     * @param query an sql query
     * @return the resulting scalar
     */
    public Object evaluate(String query) {
        PooledStatement statement = null;
        try {
            statement=statementPool.getStatement();
            return statement.evaluate(query);
        }
        catch (SQLException sqle) {
            Logger.log(sqle);
            return null;
        }
    }

    /** prepare a query
     *
     * @param query an sql query
     * @return the pooled prepared statement corresponding to the query
     */
    public PooledPreparedStatement prepare(String query) {
        PooledPreparedStatement statement = null;
        try {
            statement = preparedStatementPool.getPreparedStatement(query);
            return statement;
        }
        catch (SQLException sqle) {
            Logger.log(sqle);
            return null;
        }
    }

    /** prepare a query which is part of a transaction
     *
     * @param query an sql query
     * @return the prepared statemenet corresponding to the query
     */
    public PooledPreparedStatement transactionPrepare(String query) {
        PooledPreparedStatement statement = null;
        try {
            statement = transactionPreparedStatementPool.getPreparedStatement(query);
            return statement;
        }
        catch (SQLException sqle) {
            Logger.log(sqle);
            return null;
        }
    }

    /** issues an update query
     *
     * @param query an sql query
     * @return the number of affected rows
     */
    public int update(String query) {
        try {
               PooledStatement statement = statementPool.getStatement();
            return statement.update(query);
        }
        catch (SQLException sqle) {
            Logger.log(sqle);
            return -1;
        }
    }

    /** issue an update query that is part of a transaction
     *
     * @param query an sql query
     * @return the number of affected rows
     */
    public int transactionUpdate(String query) {
        try {
            PooledStatement statement = transactionStatementPool.getStatement();
            return statement.update(query);
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
        connectionPool.clear();
        connectionPool = null;
        transactionConnectionPool.clear();
        transactionConnectionPool = null;
        statementPool.clear();
        statementPool = null;
        transactionStatementPool.clear();
        transactionStatementPool = null;
        preparedStatementPool.clear();
        preparedStatementPool = null;
        transactionPreparedStatementPool.clear();
        transactionPreparedStatementPool = null;
    }

    /** display statistics about the statements pools
     */
    public void displayStats() {
        System.out.println("DB statistics:");
        int [] normalStats = statementPool.getUsageStats();
        int [] preparedStats = preparedStatementPool.getUsageStats();
        System.out.println("\tsimple statements   - "+normalStats[0]+" free statements out of "+normalStats[1]);
        System.out.println("\tprepared statements - "+preparedStats[0]+" free statements out of "+preparedStats[1]);
    }

    /** get a jdbc connection
     *
     * @return a jdbc connection wrapper (which extends java.sql.Connection)
     */
    public ConnectionWrapper getConnection() throws SQLException {
        return connectionPool.getConnection();
    }

    /** get the underlying jdbc connection used for transactions, and mark it right away as busy
     *
     * @return a jdbc connection wrapper (which extends java.sql.Connection)
     */
    public synchronized ConnectionWrapper getTransactionConnection() throws SQLException {
        ConnectionWrapper ret = transactionConnectionPool.getConnection();
        ret.enterBusyState();
        return ret;
    }

    /** read configuration from the given input stream
     *
     * @param config input stream on the config file
     * @exception SQLException thrown by the database engine
     */
    private void readConfigFile(InputStream config,XIncludeResolver xincludeResolver) throws SQLException,IOException {
        try {
            new ConfigLoader(this,xincludeResolver).loadConfig(config);

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
        switch(caseSensivity) {
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
        Entity previous = entities.put(adaptCase(name),entity);
        if (previous != null) {
            Logger.warn("replacing an existing entity with a new one ("+name+")");
        }
        if(name.equals("velosurf.root")) {
            /* this is the root entity */
            rootEntity = entity;
        }
    }

    public Entity getRootEntity() {
        return rootEntity;
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
            entity = new Entity(this,name,readOnly,caching);
            entities.put(adaptCase(name),entity);
        }
        return entity;
    }

    /** get an existing entity
     *
     * @param name the name of an entity
     * @return the named entity
     */
    public Entity getEntity(String name) {
        int i;
        Entity entity=(Entity)entities.get(adaptCase(name));
        if (entity == null && name != null && (i=name.indexOf('.')) != -1) {
            // imported from another schema ?
            String schema = name.substring(0,i);
            name = name.substring(i+1);
            Map external = (Map)sharedCatalog.get(getMagicNumber(schema));
            if (external != null) entity = (Entity)external.get(name);
        }
        return entity;
    }

    public Map<String,Entity> getEntities() {
        return entities;
    }

    /** get a root attribute
     *
     * @param name name of an attribute
     * @return the named attribute
     */
    public Attribute getAttribute(String name) {
        return rootEntity.getAttribute(adaptCase(name));
    }

    /** get a root action
     *
     * @param name name of an attribute
     * @return the named attribute
     */
    public Action getAction(String name) {
        return rootEntity.getAction(adaptCase(name));
    }

    /** obfuscate the given value
     * @param value value to obfuscate
     *
     * @return obfuscated value
     */
    public String obfuscate(Object value)
    {
        if (value == null) return null;
        String encoded = cryptograph.encrypt(value.toString());

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

        ret = cryptograph.decrypt(ret);

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
        return driverInfo;
    }

    /** get database case-sensivity
     *
     * @return case-sensivity
     */
    public int getCaseSensivity() {
        return caseSensivity;
    }

    /** get the integer key used to share schema entities among instances
     */
    private Integer getMagicNumber(String schema) {
        // url is not checked for now because for some databases, the schema is part of the url.
        return Integer.valueOf((user/*+url*/+schema).hashCode());
    }

    /** get the schema
     * @return the schema
     */
    public String getSchema() {
        return schema;
    }

    /** database user
     */
    protected String user = null;
    /** database user's password
     */
    protected String password = null;
    /** database url
     */
    protected String url = null;
    /** schema
     */
    protected String schema = null;

    /** whether the JDBC driver has been loaded */
    protected boolean driverLoaded = false;

    /** driver class name, if provided in the config file
     */
    protected String driverClass = null;

    /**
     * Pool of connections
     */
    protected ConnectionPool connectionPool = null;
    protected int minConnections = 1; // applies to connectionPool (min connections is always 1 for transactionConnectionPool)
    protected int maxConnections = 50; // applies to connectionPool and transactionConnectionPool

    /**
     * Pool of connections for transactions
     */
    protected ConnectionPool transactionConnectionPool = null;

    /** pool of statements
     */
    protected StatementPool statementPool = null;

    /** pool of statements for transactions
     */
    protected StatementPool transactionStatementPool = null;

    /** pool of prepared statements
     */
    protected PreparedStatementPool preparedStatementPool = null;

    /** pool of prepared statements for transactions
     */
    protected PreparedStatementPool transactionPreparedStatementPool = null;

    /** default access mode
     */
    protected boolean readOnly = true;
    /** default caching mode
     */
    protected int caching = Cache.NO_CACHE;

    /** map name->entity
     */
    protected Map<String,Entity> entities = new HashMap<String,Entity>();

    /** root entity that contains all root attributes and actions
     */
    protected Entity rootEntity = null;

    /** driver infos (database vendor specific)
     */
    protected DriverInfo driverInfo = null;

    /** random seed used to initialize the cryptograph
     */
    private String seed = null;

    /** cryptograph used to encrypt/decrypt database ids
     */
    private Cryptograph cryptograph = null;

    /** case-sensitive policy */
    public static final int CASE_UNKNOWN = 0;
    public static final int CASE_SENSITIVE = 1;
    public static final int UPPERCASE = 2;
    public static final int LOWERCASE = 3;

    /** case-sensivity */
    protected int caseSensivity = CASE_UNKNOWN;

    /** case-sensivity for context
     */
    private static int contextCase = LOWERCASE;

    /* context case implemented as a system property for now...
     *TODO: check also other configuration realms or use model.xml
     */
    static {
        String contextCase = System.getProperty("velosurf.case");
        if (contextCase != null) {
            if ("uppercase".equals(contextCase)) {
                Database.contextCase = UPPERCASE;
            } else if ("lowercase".equals(contextCase)) {
                Database.contextCase = LOWERCASE;
            } else {
                Logger.error("system property 'velosurf.case' should be 'lowercase' or 'uppercase'");
            }
        }
    }

    public static String adaptContextCase(String str) {
        if(str == null) {
            return null;
        }
        switch(contextCase) {
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
    private static Map connectionsByParams = new HashMap();

    /** map config files -> instances */
    private static Map connectionsByConfigFile = new HashMap();

    /** Shared catalog, to share entities among instances.
     * <br>
     * Key is hashcode of (name+password+url+schema), value is an entities map.
     */
    private static Map sharedCatalog = new HashMap();

    protected ReverseEngineer reverseEngineer = new ReverseEngineer(this);

}
