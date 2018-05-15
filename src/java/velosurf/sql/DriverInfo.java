package velosurf.sql;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import velosurf.util.Logger;

/**
 * <p>Contains specific description and behaviour of jdbc drivers.</p>
 *
 * <p>Main sources:
 * <ul><li>http://www.schemaresearch.com/products/srtransport/doc/modules/jdbcconf.html
 * <li>http://db.apache.org/torque/ and org.apache.torque.adapter classes
 * </ul></p>
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */
public class DriverInfo implements Serializable
{
    /**
     * Get a driver info by url and driver class.
     * @param url database url
     * @param driverClass driver class
     * @return driver infos
     */
    static public DriverInfo getDriverInfo(String url, String driverClass)
    {
        /* always try to use both infos to check for validity */
        String vendor = null;

        try
        {
            Matcher matcher = Pattern.compile("^jdbc:([^:]+):").matcher(url);

            if(matcher.find())
            {
                vendor = matcher.group(1);
            }
            else
            {
                Logger.warn("Could not guess JDBC vendor from URL " + url);
                Logger.warn("Please report this issue on Velosurf bug tracker.");
            }
        }
        catch(PatternSyntaxException pse)
        {
            Logger.log(pse);
        }

        DriverInfo ret = null, ret1 = null, ret2 = null;

        if(vendor != null)
        {
            ret1 = driverByVendor.get(vendor);
            if(ret1 == null)
            {
                Logger.warn("Velosurf doesn't know JDBC vendor '" + vendor + "'. Please contribute!");
            }
        }
        if(driverClass != null)
        {
            ret2 = driverByClass.get(driverClass);
        }
        if(ret1 == null && ret2 == null)
        {
            String msg = "No driver infos found for: ";

            if(driverClass != null)
            {
                msg += "class " + driverClass + ", ";
            }
            if(vendor != null)
            {
                msg += "vendor " + vendor;
            }
            Logger.warn(msg);
            Logger.warn("Please contribute! See http://velosurf.sf.net/velosurf/docs/drivers.html");
        }
        else if(ret1 != null && ret2 != null)
        {
            if(ret1.equals(ret2))
            {
                ret = ret1;
            }
            else
            {
                Logger.warn("Driver class '" + driverClass + "' and driver vendor '" + vendor + "' do not match!");
                Logger.warn("Please report this issue on Velosurf bug tracker.");
                ret = ret2;
            }
        }
        else if(ret1 != null)
        {
            if(driverClass != null)
            {
                Logger.warn("Driver class '" + driverClass
                            + "' is not referenced in Velosurf as a known driver for vendor '" + vendor + "'");
                Logger.warn("Please report this issue on Velosurf bug tracker.");

                /* not even sure this new driver will have the same behaviour... */
                ret1.drivers = new String[] { driverClass };
            }
            ret = ret1;
        }
        else if(ret2 != null)
        {
            ret = ret2;    /* already warned */
        }
        if(ret == null)
        {
            Logger.warn("Using default driver behaviour...");
            ret = (DriverInfo)driverByVendor.get("unknown");
        }
        return ret;
    }

    /**
     * Driver info constructor.
     * @param name name
     * @param jdbcTag jdbc tag
     * @param drivers array of driver class names
     * @param pingQuery ping query (e.g. "select 1")
     * @param caseSensivity default case sensivity policy
     * @param schemaQuery query to change schema
     * @param IDGenerationMethod preferred ID generation method
     * @param lastInsertIDQuery query to get last inserted ID value
     * @param ignorePattern ignore tables whose name matches this pattern
     */
    private DriverInfo(String name, String jdbcTag, String drivers[], String pingQuery, String caseSensivity,
                       String schemaQuery, String IDGenerationMethod, boolean pedanticColumnTypes, boolean usesGeneratedKeys, String lastInsertIDQuery, String ignorePattern)
    {
        this.name = name;
        this.jdbcTag = jdbcTag;
        this.drivers = drivers;
        this.pingQuery = pingQuery;
        this.caseSensivity = caseSensivity;
        this.schemaQuery = schemaQuery;
        this.IDGenerationMethod = IDGenerationMethod;
        this.pedanticColumnTypes = pedanticColumnTypes;
        this.usesGeneratedKeys = usesGeneratedKeys;
        this.lastInsertIDQuery = lastInsertIDQuery;
        this.ignorePattern = (ignorePattern == null ? null : Pattern.compile(ignorePattern));

//      this.IDGenerationQuery = IDGenerationQuery;
    }

    /** name of the database vendor */
    private String name;

    /** jdbc tag of the database vendor */
    private String jdbcTag;

    /** list of driver classes */
    private String[] drivers;

    /** ping SQL query */
    private String pingQuery;

    /** case-sensivity */
    private String caseSensivity;

    /** SQL query to set the current schema */
    private String schemaQuery;

    /** ID generation method */
    private String IDGenerationMethod;

    /** whether the JDBC driver is pedantic about column types */
    private boolean pedanticColumnTypes;

    /** whether executeUpdate supports RETURN_GENERATED_KEYS */
    private boolean usesGeneratedKeys;

    /** query used to retrieve the last inserted id */
    private String lastInsertIDQuery;

    /** ignore tables matchoing this pattern */
    private Pattern ignorePattern;

//  not yet implemented (TODO)
//    public String IDGenerationQuery;   // ID generation query

    /**
     *  Add a new driver.
     * @param name name
     * @param jdbcTag jdbc tag
     * @param drivers array of driver class names
     * @param pingQuery ping query (e.g. "select 1")
     * @param caseSensivity default case sensivity policy
     * @param schemaQuery query to change schema
     * @param IDGenerationMethod preferred ID generation method
     * @param pedanticColumnTypes whether the driver wants exact types for insert/update queries
     * @param usesGeneratedKeys whether the driver supports the RETURN_GENERATED_KEYS flag
     * @param lastInsertIDQuery query to get last inserted ID value
     * @param ignorePrefix ignore tables whose name matches this pattern
     */
    public static void addDriver(String name, String jdbcTag, String drivers[], String pingQuery, String caseSensivity,
                                 String schemaQuery, String IDGenerationMethod, boolean pedanticColumnTypes,
                                 boolean usesGeneratedKeys, String lastInsertIDQuery, String ignorePrefix)
    {
        DriverInfo infos = new DriverInfo(name, jdbcTag, drivers, pingQuery, caseSensivity, schemaQuery,
                IDGenerationMethod, pedanticColumnTypes, usesGeneratedKeys, lastInsertIDQuery, ignorePrefix);

        driverByVendor.put(jdbcTag, infos);
        for(String clazz : drivers)
        {
            driverByClass.put(clazz, infos);
        }
    }

    /** map jdbctag -> driver infos. */
    static private Map<String, DriverInfo> driverByVendor = new HashMap<String, DriverInfo>();

    /** map driver class -> driver infos. */
    static private Map<String, DriverInfo> driverByClass = new HashMap<String, DriverInfo>();

    /**
     * Get the jdbc tag.
     * @return jdbc tag
     */
    public String getJdbcTag()
    {
        return jdbcTag;
    }

    /**
     * Get the list of driver class names.
     * @return array of driver class names
     */
    public String[] getDrivers()
    {
        return drivers;
    }

    /**
     * Get the ping query.
     * @return ping query
     */
    public String getPingQuery()
    {
        return pingQuery;
    }

    /**
     * Get case sensivity default policy.
     * @return case sensivity default policy
     */
    public String getCaseSensivity()
    {
        return caseSensivity;
    }

    /**
     * Get the schema setter query.
     * @return schema setter query
     */
    public String getSchemaQuery()
    {
        return schemaQuery;
    }

    /**
     * Get whether the engine is pedantic about column types
     * @return boolean
     */
    public boolean getPedanticColumnTypes()
    {
        return pedanticColumnTypes;
    }

    /**
     * Get whether the engine supports the RETURN_GENERATED_KEYS JDBC flag
     * @return boolean
     */
    public boolean getUsesGeneratedKeys()
    {
        return usesGeneratedKeys;
    }

    /**
     * Get the last inserted id.
     * @param statement source statement
     * @return last inserted id (or -1)
     * @throws SQLException
     */
    public Object getLastInsertId(Statement statement) throws SQLException
    {
        long ret = -1;

        if("mysql".equalsIgnoreCase(getJdbcTag()))
        {    /* MySql */
            try
            {
                Method lastInsertId = statement.getClass().getMethod("getLastInsertID", new Class[0]);

                ret = ((Long)lastInsertId.invoke(statement, new Object[0])).longValue();
            }
            catch(Throwable e)
            {
                Logger.log("Could not find last insert id: ", e);
            }
        }
        else if (getUsesGeneratedKeys())
        {
			int col = 1;
			ResultSet rs = statement.getGeneratedKeys();
			ResultSetMetaData rsmd = rs.getMetaData();
			int numberOfColumns = rsmd.getColumnCount();
			rs.next();
			if (numberOfColumns > 1)
			{
				Map<String,Object> res = new HashMap<String,Object>();
				Logger.warn("Number of columns for generated keys > 1, return a map instead");
				for (int i = 1; i <= numberOfColumns ; i++)
				{
					Logger.debug("Column " + rsmd.getColumnName(i) + " of type " + rsmd.getColumnClassName(i));
					res.put(rsmd.getColumnName(i),rs.getObject(i));
				}
				return res;
			}
			else
			{
				ret = new Long(rs.getLong(col));
			}
        }
        else
        {
            if(lastInsertIDQuery == null)
            {
                Logger.error("getLastInsertID is not [yet] implemented for your dbms... Contribute!");
            }
            else
            {
                ResultSet rs = statement.getConnection().createStatement().executeQuery(lastInsertIDQuery);

                rs.next();
                ret = rs.getLong(1);
            }
        }
        return ret;
    }

    /**
     * Check whether to ignore or not this table.
     *
     * @param name table name
     * @return whether to ignore this table
     */
    public boolean ignoreTable(String name)
    {
        return ignorePattern != null && ignorePattern.matcher(name).matches();
    }

    // sources :
    // http://www.schemaresearch.com/products/srtransport/doc/modules/jdbcconf.html
    // http://db.apache.org/torque/ and org.apache.torque.adapter classes
    // and Google of course
    static
    {
        addDriver("DB2", "db2", new String[] { "COM.ibm.db2.jdbc.app.DB2Driver", "COM.ibm.db2.jdbc.net.DB2Driver" },
                  "select 1", "TODO", "TODO", "none", false, false, "VALUES IDENTITY_VAL_LOCAL()", null);
        addDriver("Derby", "derby", new String[] { "org.apache.derby.jdbc.EmbeddedDriver" }, "values 1", "uppercase",
                  "set schema $schema", "autoincrement", false, false, null, null);
        addDriver("Easysoft", "easysoft", new String[] { "easysoft.sql.jobDriver" }, "select 1", "TODO", "TODO",
                  "TODO", false, false, null, null);
        addDriver("Firebird", "firebirdsql", new String[] { "org.firebirdsql.jdbc.FBDriver" }, "TODO", "TODO", "TODO",
                  "TODO", false, true, null, null);
        addDriver("Frontbase", "frontbase", new String[] { "jdbc.FrontBase.FBJDriver" }, "select 1", "TODO", "TODO",
                  "TODO", false, false, null, null);
        addDriver("HSQLDB", "hsqldb", new String[] { "org.hsqldb.jdbcDriver", "org.hsql.jdbcDriver" }, "call 1",
                  "uppercase", "set schema $schema", "autoincrement", false, false, "CALL IDENTITY()", "SYSTEM_.*");
        addDriver("Hypersonic", "hypersonic", new String[] { "org.hsql.jdbcDriver" }, "select 1", "TODO", "TODO",
                  "autoincrement", false, false, null, null);
        addDriver("OpenBase", "openbase", new String[] { "com.openbase.jdbc.ObDriver" }, "select 1", "TODO", "TODO",
                  "TODO", false, false, null, null);
        addDriver("Informix", "informix", new String[] { "com.informix.jdbc.IfxDriver" }, "select 1", "TODO", "TODO",
                  "none", false, false, null, null);
        addDriver("InstantDB", "instantdb", new String[] { "org.enhydra.instantdb.jdbc.idbDriver" }, "select 1",
                  "TODO", "TODO", "none", false, false, null, null);
        addDriver("Interbase", "interbase", new String[] { "interbase.interclient.Driver" }, "select 1", "TODO",
                  "TODO", "none", false, false, null, null);
        addDriver("ODBC", "odbc", new String[] { "sun.jdbc.odbc.JdbcOdbcDriver" }, "select 1", "TODO", "TODO", "TODO",
                false, false, null, null);
        addDriver("Sql Server", "sqlserver", new String[] { "com.microsoft.jdbc.sqlserver.SQLServerDriver",
            "com.jnetdirect.jsql.JSQLDriver", "com.merant.datadirect.jdbc.sqlserver.SQLServerDriver" }, "select 1",
            "TODO", "TODO", "autoincrement", false, true, null, null);
        addDriver("MySql", "mysql", new String[] { "com.mysql.jdbc.Driver", "org.gjt.mm.mysql.Driver" }, "select 1",
                  "sensitive", null, "autoincrement", false, false, null, null);
        addDriver("OpenBase", "", new String[] { "com.openbase.jdbc.ObDriver" }, "select 1", "TODO", "TODO", "TODO",
                false, false, null, null);
        addDriver("Oracle", "oracle", new String[] { "oracle.jdbc.driver.OracleDriver" }, "select 1 from dual",
                  "uppercase", "alter session set current_schema = $schema", "sequence", false, true, null, ".*\\/.*");
        addDriver("PostgreSQL", "postgresql", new String[] { "org.postgresql.Driver" }, "select 1", "lowercase", null,
                  "autoincrement", true, true, null, "pg_*");    // also sequences, but support for autoincrement is better
        addDriver("SapDB", "sapdb", new String[] { "com.sap.dbtech.jdbc.DriverSapDB" }, "select 1 from dual",
                  "uppercase", "TODO", "sequence", false, false, null, null);
        addDriver("Sybase", "sybase", new String[] { "com.sybase.jdbc2.jdbc.SybDriver" }, "select 1", "TODO", "TODO",
                  "autoincrement", false, false, "SELECT @@IDENTITY", null);
        addDriver("Weblogic", "weblogic", new String[] { "weblogic.jdbc.pool.Driver" }, "select 1", "TODO", "TODO",
                  "none", false, false, null, null);

        // unknwon driver
        addDriver("Unknown driver", "unknown", new String[] {}, "select 1", "sensitive", null, "none", false, false,
                null, null);
    }

    /**
     * Driver-specofic value filtering
     *
     * @param value value to be filtered
     * @return filtered value
     */
    public Object filterValue(Object value)
    {
        if(value instanceof Calendar && "mysql".equals(jdbcTag))
        {
            value = ((Calendar)value).getTime();
        }
        return value;
    }
}
