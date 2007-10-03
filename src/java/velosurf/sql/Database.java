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
import velosurf.util.UserContext;

/** This class encapsulates  a connection to the database and contains all the stuff relative to it.
 *
 *  <p>To get a new instance, client classes should call one of the getInstance static methods.</p>
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 *
 */
public class Database {

    /** Builds a new connection.
     *
     */
    private Database() {
    }

    /** Builds a new connection.
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

    /** Get a unique Database from connection params.
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

    /** Get a unique Database from connection params.
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

    /** Get a unique Database from connection params.
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

    /** Get a unique Database from config filename.
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

    /** Get a new connection.
     * @param config config filename
     * @exception SQLException thrown by the database engine
     * @return a new connection
     */
    public static Database getInstance(InputStream config) throws SQLException {
        return Database.getInstance(config,null);
    }

    /** Get a new connection.
     * @param config config filename
     * @exception SQLException thrown by the database engine
     * @return a new connection
     */
    public static Database getInstance(InputStream config,XIncludeResolver xincludeResolver) throws SQLException {
        Database instance = new Database();
        instance.readConfigFile(config,xincludeResolver);
		instance.initCryptograph();
        instance.connect();
        instance.getReverseEngineer().readMetaData();
        return instance;
    }

    /** Open the connection.
     *
     * @param user user name
     * @param password password
     * @param url database url
     * @param driver driver java class name
     * @param schema schema name
     * @exception SQLException thrown by the database engine
     */
    private void open(String user,String password,String url,String driver,String schema) throws SQLException {

        this.user = user;
        this.password = password;
        this.url = url;
        this.schema = schema;
        driverClass = driver;
		initCryptograph();
        connect();
    }
    /** Connect the database.
     *
     * @throws SQLException
     */
    private void connect() throws SQLException
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
    /**
     * Set the read-only state.
     * @param readOnly read-only state
     */
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }
    /**
     * Set the caching method.
     * @param cachingMethod caching method
     */
    public void setCaching(int cachingMethod) {
        caching = cachingMethod;
    }
    /** Set the database user.
     *
     * @param user user name.
     */
    public void setUser(String user) {
        this.user = user;
    }
    /**
     * Set the database password.
     * @param password password
     */
    public void setPassword(String password) {
       this.password = password;
    }
    /**
     * Set the database URL.
     * @param url database url
     */
    public void setURL(String url) {
        this.url = url;
    }
    /**
     * Set driver class.
     * @param driverClass driver class
     */
    public void setDriver(String driverClass) {
        this.driverClass = driverClass;
    }
    /**
     * Set schema name.
     * @param schema schema name
     */
    public void setSchema(String schema) {
        this.schema = schema;
        if(this.schema != null) {
            // share entities
            sharedCatalog.put(getMagicNumber(this.schema),entities);
        }
    }
    /**
     * Set minimum number of connections.
     * @param minConnections minimum number of connections
     */
    public void setMinConnections(int minConnections) {
        this.minConnections = minConnections;
    }
    /**
     * Set the maximum number of connections.
     * @param maxConnections maximum number of connections
     */
    public void setMaxConnections(int maxConnections) {
        this.maxConnections = maxConnections;
    }
    /**
     * Set the encryption seed.
     * @param seed encryption seed
     */
    public void setSeed(String seed) {
        this.seed = seed;
    }
    /**
     * Set the case policy.
     * Possible values are CASE_SENSITIVE, CASE_LOWERCASE and CASE_UPPERCASE.
     * @param caseSensivity case policy
     */
    public void setCase(int caseSensivity) {
        this.caseSensivity = caseSensivity;
    }

    /** Load the appropriate driver.
     */
    @SuppressWarnings("deprecation") protected void loadDriver() {

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
    /** Init cryptograph.
     *
     */
    protected void initCryptograph()
    {
        if (cryptograph != null) return;
        // to initialize the cryptograph, we need a chunk of user-provided bytes
        // they must be persistent, so that urls that use encrypted params remain valid
        // => use the database url if null
        if (seed == null) {
            seed = url;
        }
        try {
            cryptograph = (Cryptograph)Class.forName("velosurf.util.DESCryptograph").getDeclaredConstructor(new Class[] {}).newInstance(new Object[] {});
            cryptograph.init(seed);
        }
        catch(Exception e) {
            Logger.error("Cannot initialize the cryptograph");
            Logger.log(e);
        }
    }
    /**
     * Get reverse engineer.
     * @return reverse engineer.
     */
    public ReverseEngineer getReverseEngineer() {
        return reverseEngineer;
    }

    /** Issue a query.
     *
     * @param query an SQL query
     * @return the resulting RowIterator
     */
    public RowIterator query(String query) throws SQLException {
        return query(query,null);
    }

    /** Issue a query, knowing the resulting entity.
     *
     * @param query an SQL query
     * @param entity the resulting entity
     * @return return the resulting row iterator
     */
    public RowIterator query(String query,Entity entity) throws SQLException {
        PooledSimpleStatement statement = null;
        statement=statementPool.getStatement();
        return statement.query(query,entity);
    }

    /** Evaluate a query to a scalar.
     *
     * @param query an sql query
     * @return the resulting scalar
     */
    public Object evaluate(String query) {
        PooledSimpleStatement statement = null;
        try {
            statement=statementPool.getStatement();
            return statement.evaluate(query);
        }
        catch (SQLException sqle) {
            Logger.log(sqle);
            return null;
        }
    }

    /** Prepare a query.
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

    /** Prepare a query which is part of a transaction.
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

    /** Issue an update query.
     *
     * @param query an sql query
     * @return the number of affected rows
     */
    public int update(String query) {
        try {
               PooledSimpleStatement statement = statementPool.getStatement();
            return statement.update(query);
        }
        catch (SQLException sqle) {
            Logger.log(sqle);
            return -1;
        }
    }

    /** Issue an update query that is part of a transaction.
     *
     * @param query an sql query
     * @return the number of affected rows
     */
    public int transactionUpdate(String query) {
        try {
            PooledSimpleStatement statement = transactionStatementPool.getStatement();
            return statement.update(query);
        }
        catch (SQLException sqle) {
            Logger.log(sqle);
            return -1;
        }
    }

    /** Close the connection.
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

    /** Display statistics about the statements pools.
     */
    public void displayStats() {
        System.out.println("DB statistics:");
        int [] normalStats = statementPool.getUsageStats();
        int [] preparedStats = preparedStatementPool.getUsageStats();
        System.out.println("\tsimple statements   - "+normalStats[0]+" free statements out of "+normalStats[1]);
        System.out.println("\tprepared statements - "+preparedStats[0]+" free statements out of "+preparedStats[1]);
    }

    /** Get a jdbc connection.
     *
     * @return a jdbc connection wrapper (which extends java.sql.Connection)
     */
    public ConnectionWrapper getConnection() throws SQLException {
        ConnectionWrapper c = connectionPool.getConnection();
        c.setReadOnly(readOnly);
        return c;
    }

    /** Get the underlying jdbc connection used for transactions, and mark it right away as busy.
     *
     * @return a jdbc connection wrapper (which extends java.sql.Connection)
     */
    public synchronized ConnectionWrapper getTransactionConnection() throws SQLException {
        ConnectionWrapper ret = transactionConnectionPool.getConnection();
        ret.setReadOnly(readOnly);
        ret.enterBusyState();
        return ret;
    }

    /** Read configuration from the given input stream.
     *
     * @param config input stream on the config file
     */
    private void readConfigFile(InputStream config,XIncludeResolver xincludeResolver) {
        try {
            new ConfigLoader(this,xincludeResolver).loadConfig(config);

        } catch (Exception e) {
            Logger.error("could not load configuration!");
            Logger.log(e);
        }
    }

    /** Changes to lowercase or uppercase if needed.
     *
     * @param identifier
     * @return changed identifier
     */
    public String adaptCase(String identifier) {
        if (identifier == null) return null;
        String ret;
        switch(caseSensivity) {
            case CASE_SENSITIVE:
                ret = identifier;
                break;
            case UPPERCASE:
                ret = identifier.toUpperCase();
                break;
            case LOWERCASE:
                ret = identifier.toLowerCase();
                break;
            default:
                Logger.error("bad case-sensivity!");
                ret = identifier;
        }
        return ret;
    }

    /** Add a new entity.
     *
     * @param entity entity to add
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
    /**
     * Get root entity.
     * @return root entity
     */
    public Entity getRootEntity() {
        return rootEntity;
    }

    /** Get a named entity or creeate it if it doesn't exist
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

    /** Get an existing entity.
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
    /** Entities map getter.
     *
     * @return entities map
     */
    public Map<String,Entity> getEntities() {
        return entities;
    }

    /** Get a root attribute.
     *
     * @param name name of an attribute
     * @return the named attribute
     */
    public Attribute getAttribute(String name) {
        return rootEntity.getAttribute(adaptCase(name));
    }

    /** Get a root action.
     *
     * @param name name of an attribute
     * @return the named attribute
     */
    public Action getAction(String name) {
        return rootEntity.getAction(adaptCase(name));
    }

    /** Obfuscate the given value.
     * @param value value to obfuscate
     *
     * @return obfuscated value
     */
    public String obfuscate(Object value)
    {
        if (value == null) return null;
        String encoded = cryptograph.encrypt(value.toString());

        // we want to avoid some characters fot HTTP GET
        encoded = encoded.replace('=','$');
        encoded = encoded.replace('/','_');
        encoded = encoded.replace('+','-');

        return encoded;
    }

    /** De-obfuscate the given value.
     * @param value value to de-obfuscate
     *
     * @return obfuscated value
     */
    public String deobfuscate(Object value)
    {
        if (value == null) return null;

        String ret = value.toString();

        // recover exact encoded string
        ret = ret.replace('$','=');
        ret = ret.replace('_','/');
        ret = ret.replace('-','+');

        ret = cryptograph.decrypt(ret);

        if (ret == null) {
            Logger.error("deobfuscation of value '"+value+"' failed!");
            return null;
        }

        return ret;
    }

    /** Get database driver infos.
     * @return the database vendor
     */
    public DriverInfo getDriverInfo() {
        return driverInfo;
    }

    /** Get database case-sensivity policy.
     *
     * @return case-sensivity
     */
    public int getCaseSensivity() {
        return caseSensivity;
    }

    /** Get the integer key used to share schema entities among instances.
     */
    private Integer getMagicNumber(String schema) {
        // url is not checked for now because for some databases, the schema is part of the url.
        return Integer.valueOf((user/*+url*/+schema).hashCode());
    }

    /** Get the schema.
     * @return the schema
     */
    public String getSchema() {
        return schema;
    }

    /** database user.
     */
    private String user = null;
    /** database user's password.
     */
    private String password = null;
    /** database url.
     */
    private String url = null;
    /** schema.
     */
    private String schema = null;

    /** whether the JDBC driver has been loaded. */
    private boolean driverLoaded = false;

    /** driver class name, if provided in the config file.
     */
    private String driverClass = null;

    /**
     * Pool of connections.
     */
    private ConnectionPool connectionPool = null;
    /** min connections. */
    private int minConnections = 1; // applies to connectionPool (min connections is always 1 for transactionConnectionPool)
    /** max connections. */
    private int maxConnections = 50; // applies to connectionPool and transactionConnectionPool

    /**
     * Pool of connections for transactions.
     */
    private ConnectionPool transactionConnectionPool = null;

    /** pool of statements.
     */
    private StatementPool statementPool = null;

    /** pool of statements for transactions.
     */
    private StatementPool transactionStatementPool = null;

    /** pool of prepared statements.
     */
    private PreparedStatementPool preparedStatementPool = null;

    /** pool of prepared statements for transactions.
     */
    private PreparedStatementPool transactionPreparedStatementPool = null;

    /** default access mode.
     */
    private boolean readOnly = true;
    /** default caching mode.
     */
    private int caching = Cache.NO_CACHE;

    /** map name->entity.
     */
    private Map<String,Entity> entities = new HashMap<String,Entity>();

    /** root entity that contains all root attributes and actions.
     */
    private Entity rootEntity = null;

    /** driver infos (database vendor specific).
     */
    private DriverInfo driverInfo = null;

    /** random seed used to initialize the cryptograph.
     */
    private String seed = null;

    /** cryptograph used to encrypt/decrypt database ids.
     */
    private Cryptograph cryptograph = null;

    /** unknown case-sensitive policy. */
    public static final int CASE_UNKNOWN = 0;
    /** sensitive case-sensitive policy. */
    public static final int CASE_SENSITIVE = 1;
    /** uppercase case-sensitive policy. */
    public static final int UPPERCASE = 2;
    /** lowercase case-sensitive policy. */
    public static final int LOWERCASE = 3;

    /** case-sensivity. */
    private int caseSensivity = CASE_UNKNOWN;

    /** case-sensivity for context.
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
    /** adapt a string to the context case.
     *
     * @param str string to adapt
     * @return adapted string
     */
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

    /** Set this database user context (thread local)
     *  @param userContext user context
     */
    public void setUserContext(UserContext userContext) {
        this.userContext.set(userContext);
    }

    /** Get this database user context (thread local)
     *
     * @return the thread local user context
     */
    public UserContext getUserContext() {
        UserContext ret = userContext.get();
        if (ret == null) {
            /* create one */
            ret = new UserContext();
            userContext.set(ret);
        }
        return ret;
    }

    public void setError(String errormsg) {
        getUserContext().setError(errormsg);
    }

    /** map parameters -> instances. */
    private static Map<Integer,Database> connectionsByParams = new HashMap<Integer,Database>();

    /** map config files -> instances. */
    private static Map<Integer,Database> connectionsByConfigFile = new HashMap<Integer,Database>();

    /** <p>Shared catalog, to share entities among instances.</p>
     *
     * <p>Key is hashcode of (name+password+url+schema), value is an entities map.</p>
     */
    private static Map<Integer,Map<String,Entity>> sharedCatalog = new HashMap<Integer,Map<String,Entity>>();
    /**
     * Reverse engineer.
     */
    private ReverseEngineer reverseEngineer = new ReverseEngineer(this);

    /** Thread-local user context.
     */
    private ThreadLocal<UserContext> userContext = new ThreadLocal<UserContext>();

}
