package velosurf.sql;

import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.regex.PatternSyntaxException;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.lang.reflect.Method;

import velosurf.util.Logger;

/**
 * <p>Contains specific description and behaviour of jdbc drivers</p>
 *
 * <p>(main sources:
 * <ul><li>http://www.schemaresearch.com/products/srtransport/doc/modules/jdbcconf.html
 * <li>http://db.apache.org/torque/ and org.apache.torque.adapter classes
 * </ul></p>
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */

public class DriverInfo
{
    static DriverInfo getDriverInfo(String url,String driverClass)
    {
        /* always try to use both infos to check for validity */
        String vendor = null;
        try {
            Matcher matcher = Pattern.compile("^jdbc:([^:]+):").matcher(url);
            if (matcher.find()) {
                vendor = matcher.group(1);
            } else {
                Logger.warn("Could not guess JDBC vendor from URL "+url);
                Logger.warn("Please report this issue on Velosurf bug tracker.");
            }
        }
        catch(PatternSyntaxException pse) {
            Logger.log(pse);
        }
        DriverInfo ret = null,ret1 = null, ret2 = null;
        if (vendor != null) {
            ret1 = driverByVendor.get(vendor);
            if (ret1 == null) {
                Logger.warn("Velosurf doesn't know JDBC vendor '"+vendor+"'. Please contribute!");
            }
        }
        if (driverClass != null) ret2 = driverByClass.get(driverClass);

        if (ret1 == null && ret2 == null) {
            String msg = "No driver infos found for: ";
            if (driverClass != null) {
                msg += "class "+driverClass+", ";
            }
            if (vendor != null) {
                msg+="vendor "+vendor;
            }
            Logger.warn(msg);
            Logger.warn("Please contribute! See http://velosurf.sf.net/velosurf/docs/drivers.html");
        } else if (ret1 != null && ret2 != null) {
            if (ret1.equals(ret2)) {
                ret = ret1;
            } else {
                Logger.warn("Driver class '"+driverClass+"' and driver vendor '"+vendor+"' do not match!");
                Logger.warn("Please report this issue on Velosurf bug tracker.");
                ret = ret2;
            }
        } else if (ret1 != null) {
            if(driverClass != null) {
                Logger.warn("Driver class '"+driverClass+"' is not referenced in Velosurf as a known driver for vendor '"+vendor+"'");
                Logger.warn("Please report this issue on Velosurf bug tracker.");
                /* not even sure this new driver will have the same behaviour... */
                ret1.drivers = new String[] {driverClass};
            }
            ret = ret1;
        } else if (ret2 != null) {
            ret = ret2; /* already warned */
        }

        if (ret == null) {
            Logger.warn("Using default driver behaviour...");
            ret = (DriverInfo)driverByVendor.get("unknown");
        }
        return ret;
    }

    public DriverInfo(String name,String jdbcTag,String drivers[],String pingQuery,String caseSensivity,String schemaQuery,String IDGenerationMethod,String lastInsertIDQuery,String ignorePattern)
    {
        this.name = name;
        this.jdbcTag = jdbcTag;
        this.drivers = drivers;
        this.pingQuery = pingQuery;
        this.caseSensivity = caseSensivity;
        this.schemaQuery = schemaQuery;
        this.IDGenerationMethod = IDGenerationMethod;
        this.lastInsertIDQuery = lastInsertIDQuery;
        this.ignorePattern = (ignorePattern == null ? null : Pattern.compile(ignorePattern));
//        this.IDGenerationQuery = IDGenerationQuery;
    }

    protected String name;                // name of the database vendor
    protected String jdbcTag;             // jdbc tag of the database vendor
    protected String[] drivers;           // list of driver classes
    protected String pingQuery;           // ping SQL query
    protected String caseSensivity;       // case-sensivity
    protected String schemaQuery;         // SQL query to set the current schema
    protected String IDGenerationMethod;  // ID generation method
    protected String lastInsertIDQuery;   // query used to retrieve the last inserted id
    protected Pattern ignorePattern;
// not yet implemented (TODO)
//   public String IDGenerationQuery;   // ID generation query

    public static void addDriver(String name,String jdbcTag,String drivers[],String pingQuery,String caseSensivity,String schemaQuery,String IDGenerationMethod,String lastInsertIDQuery,String ignorePrefix/*,String IDGenerationQuery*/)
    {
        DriverInfo infos = new DriverInfo(name,jdbcTag,drivers,pingQuery,caseSensivity,schemaQuery,IDGenerationMethod,lastInsertIDQuery,ignorePrefix/*,IDGenerationQuery*/);
        driverByVendor.put(jdbcTag,infos);
        for(String clazz:drivers) {
            driverByClass.put(clazz,infos);
        }
    }

    /* map jdbctag -> driver infos */
    static private Map<String,DriverInfo> driverByVendor = new HashMap<String,DriverInfo>();

    /* map driver class -> driver infos */
    static private Map<String,DriverInfo> driverByClass = new HashMap<String,DriverInfo>();

    public String getJdbcTag() {
        return jdbcTag;
    }

    protected String[] getDrivers() {
        return drivers;
    }

    public String getPingQuery() {
        return pingQuery;
    }

    public String getCaseSensivity() {
        return caseSensivity;
    }

    public String getSchemaQuery() {
        return schemaQuery;
    }

    public long getLastInsertId(Statement statement) throws SQLException
    {
        long ret = -1;
        if ("mysql".equalsIgnoreCase(getJdbcTag()))
        {  /* MySql */
            try
            {
                Method lastInsertId = statement.getClass().getMethod("getLastInsertID",new Class[0]);
                ret = ((Long)lastInsertId.invoke(statement,new Object[0])).longValue();
            }
            catch (Throwable e) {
                Logger.log("Could not find last insert id: ",e);
            }
        } else {
            if (lastInsertIDQuery == null) {
                Logger.error("getLastInsertID is not [yet] implemented for your dbms... Contribute!");
            } else {
                ResultSet rs = statement.executeQuery(lastInsertIDQuery);
                rs.next();
                ret = rs.getLong(1);
            }
        }
        return ret;
    }

    public boolean ignoreTable(String name) {
        return ignorePattern != null && ignorePattern.matcher(name).matches();
    }

    // sources :
    // http://www.schemaresearch.com/products/srtransport/doc/modules/jdbcconf.html
    // http://db.apache.org/torque/ and org.apache.torque.adapter classes
    // and Google of course
    static {
        addDriver("Axion","axiondb",new String[] {"org.axiondb.jdbc.AxionDriver"},"select 1","TODO","TODO","none",null,null);
        addDriver("Cloudscape","cloudscape",new String[] {"COM.cloudscape.core.JDBCDriver"},"select 1","TODO","TODO","autoincrement","VALUES IDENTITY_VAL_LOCAL()",null);
        addDriver("DB2","db2",new String[] {"COM.ibm.db2.jdbc.app.DB2Driver","COM.ibm.db2.jdbc.net.DB2Driver"},"select 1","TODO","TODO","none","VALUES IDENTITY_VAL_LOCAL()",null);
        addDriver("Derby", "derby", new String[] {"org.apache.derby.jdbc.EmbeddedDriver"}, "values 1", "uppercase", "set schema $schema", "autoincrement",null,null);
        addDriver("Easysoft","easysoft",new String[] {"easysoft.sql.jobDriver"},"select 1","TODO","TODO","TODO",null,null);
        addDriver("Firebird","firebirdsql",new String [] {"org.firebirdsql.jdbc.FBDriver"},"TODO","TODO","TODO","TODO",null,null);
        addDriver("Frontbase","frontbase",new String[] {"jdbc.FrontBase.FBJDriver"},"select 1","TODO","TODO","TODO",null,null);
        addDriver("HSQLDB","hsqldb",new String[] {"org.hsqldb.jdbcDriver","org.hsql.jdbcDriver"},"call 1","uppercase","set schema $schema","autoincrement","CALL IDENTITY()","SYSTEM_.*");
        addDriver("Hypersonic","hypersonic",new String[] {"org.hsql.jdbcDriver"},"select 1","TODO","TODO","autoincrement",null,null);
        addDriver("OpenBase","openbase",new String[] {"com.openbase.jdbc.ObDriver"},"select 1","TODO","TODO","TODO",null,null);
        addDriver("Informix","informix",new String[] {"com.informix.jdbc.IfxDriver"},"select 1","TODO","TODO","none",null,null);
        addDriver("InstantDB","instantdb",new String[] {"org.enhydra.instantdb.jdbc.idbDriver"},"select 1","TODO","TODO","none",null,null);
        addDriver("Interbase","interbase",new String[] {"interbase.interclient.Driver"},"select 1","TODO","TODO","none",null,null);
        addDriver("ODBC","odbc",new String[] {"sun.jdbc.odbc.JdbcOdbcDriver"},"select 1","TODO","TODO","TODO",null,null);
        addDriver("Sql Server","sqlserver",new String[] {"com.microsoft.jdbc.sqlserver.SQLServerDriver","com.jnetdirect.jsql.JSQLDriver","com.merant.datadirect.jdbc.sqlserver.SQLServerDriver"},"select 1","TODO","TODO","autoincrement",null,null);
        addDriver("MySql","mysql",new String[] {"com.mysql.jdbc.Driver","org.gjt.mm.mysql.Driver"},"select 1","sensitive",null,"autoincrement",null,null);
        addDriver("OpenBase","",new String[] {"com.openbase.jdbc.ObDriver"},"select 1","TODO","TODO","TODO",null,null);
        addDriver("Oracle","oracle",new String[] {"oracle.jdbc.driver.OracleDriver"},"select 1 from dual","uppercase","alter session set current_schema = $schema","sequence",null,".*\\/.*");
        addDriver("PostgreSQL","postgresql",new String[] {"org.postgresql.Driver"},"select 1","lowercase",null,"autoincrement",null,null); // also sequences, but support for autoincrement is better
        addDriver("SapDB","sapdb",new String[] {"com.sap.dbtech.jdbc.DriverSapDB"},"select 1 from dual","uppercase","TODO","sequence",null,null);
        addDriver("Sybase","sybase",new String[] {"com.sybase.jdbc2.jdbc.SybDriver"},"select 1","TODO","TODO","autoincrement","SELECT @@IDENTITY",null);
        addDriver("Weblogic","weblogic",new String[] {"weblogic.jdbc.pool.Driver"},"select 1","TODO","TODO","none",null,null);

        // unknwon driver
        addDriver("Unknown driver","unknown",new String[]{},"select 1","sensitive",null,"none",null,null);
    }
}

/* old implementation
    // array containing { vendor(as it appear in the url), driver-1, ... driver-n, CheckQuery/null, case-sensivity }
    // sources :
    // http://www.schemaresearch.com/products/srtransport/doc/modules/jdbcconf.html
    // http://db.apache.org/torque/ and org.apache.torque.adapter classes
    // and Google of course
    private static String vendors[][] =
            { // ID genetation methods are indicated at the end as comments, for further implementation
                {"Axiondb","org.axiondb.jdbc.AxionDriver","select 1",""}, // none
                {"Cloudscape","COM.cloudscape.core.JDBCDriver","select 1",""}, // autoincrement : select distinct ConnectionInfo.lastAutoIncrementValue( 'APP'|schema, table, column ) from table
                {"DB2","COM.ibm.db2.jdbc.app.DB2Driver","COM.ibm.db2.jdbc.net.DB2Driver","select 1",""}, // none
                {"Easysoft","easysoft.sql.jobDriver","select 1",""},
                {"FrontBase","jdbc.FrontBase.FBJDriver","select 1",""},
                {"HSQLDB","org.hsqldb.jdbcDriver","org.hsql.jdbcDriver","",""},
                {"Hypersonic","org.hsql.jdbcDriver","select 1",""}, // autoincrement : select IDENTIY() from ?
                {"OpenBase","com.openbase.jdbc.ObDriver","select 1",""},
                {"Informix","com.informix.jdbc.IfxDriver","select 1",""}, // none
                {"InstantDB","org.enhydra.instantdb.jdbc.idbDriver","select 1",""}, // none
                {"Interbase","interbase.interclient.Driver","select 1",""}, // none
                {"odbc","sun.jdbc.odbc.JdbcOdbcDriver","select 1",""},
                {"SqlServer","com.microsoft.jdbc.sqlserver.SQLServerDriver","com.jnetdirect.jsql.JSQLDriver","com.merant.datadirect.jdbc.sqlserver.SQLServerDriver","select 1",""}, // autoincrement : select @@identity
                {"MySql","com.mysql.jdbc.Driver","org.gjt.mm.mysql.Driver","select 1","sensitive"}, // autoincrement : select lastinsertid()
                {"Openbase","com.openbase.jdbc.ObDriver","select 1",""},
                {"Oracle","oracle.jdbc.driver.OracleDriver","select 1 from dual","uppercase"}, // sequence : select seq.nextval from dual
                {"PostgreSQL","org.postgresql.Driver","select 1",""}, // autoincrement (or sequence !) : select currval(table)
                {"SapDB","com.sap.dbtech.jdbc.DriverSapDB","select 1 from dual",""},  // sequence : select seq.nextval from dual
                {"Sybase","com.sybase.jdbc2.jdbc.SybDriver","select 1",""}, // autoincrement : select @@identity
                {"Weblogic","weblogic.jdbc.pool.Driver","select 1",""} // none
            };
*/
