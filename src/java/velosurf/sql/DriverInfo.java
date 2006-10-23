package velosurf.sql;

import java.util.Map;
import java.util.HashMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.regex.PatternSyntaxException;
import java.sql.Statement;
import java.sql.Connection;
import java.sql.ResultSet;
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
    static DriverInfo getDriverInfo(String inUrl,String inDriverClass)
    {
        /* always try to use both infos to check for validity */
        String vendor = null;
        try {
            Matcher matcher = Pattern.compile("^jdbc:([^:]+):").matcher(inUrl);
            if (matcher.find()) {
                vendor = matcher.group(1);
            } else {
                Logger.warn("Could not guess JDBC vendor from URL "+inUrl);
                Logger.warn("Please report this issue on Velosurf bug tracker.");
            }
        }
        catch(PatternSyntaxException pse) {
            Logger.log(pse);
        }
        DriverInfo ret = null,ret1 = null, ret2 = null;
        if (vendor != null) {
            ret1 = sDriverByVendor.get(vendor);
            if (ret1 == null) {
                Logger.warn("Velosurf doesn't know JDBC vendor '"+vendor+"'. Please contribute!");
            }
        }
        if (inDriverClass != null) ret2 = sDriverByClass.get(inDriverClass);

        if (ret1 == null && ret2 == null) {
            String msg = "No driver infos found for: ";
            if (inDriverClass != null) {
                msg += "class "+inDriverClass+", ";
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
                Logger.warn("Driver class '"+inDriverClass+"' and driver vendor '"+vendor+"' do not match!");
                Logger.warn("Please report this issue on Velosurf bug tracker.");
                ret = ret2;
            }
        } else if (ret1 != null) {
            if(inDriverClass != null) {
                Logger.warn("Driver class '"+inDriverClass+"' is not referenced in Velosurf as a known driver for vendor '"+vendor+"'");
                Logger.warn("Please report this issue on Velosurf bug tracker.");
                /* not even sure this new driver will have the same behaviour... */
                ret1._drivers = new String[] {inDriverClass};
            }
            ret = ret1;
        } else if (ret2 != null) {
            ret = ret2; /* already warned */
        }

        if (ret == null) {
            Logger.warn("Using default driver behaviour...");
            ret = (DriverInfo)sDriverByVendor.get("unknown");
        }
        return ret;
    }

    public DriverInfo(String name,String jdbcTag,String drivers[],String pingQuery,String caseSensivity,String schemaQuery,String IDGenerationMethod,String ignorePrefix)
    {
        _name = name;
        _jdbcTag = jdbcTag;
        _drivers = drivers;
        _pingQuery = pingQuery;
        _caseSensivity = caseSensivity;
        _schemaQuery = schemaQuery;
        _IDGenerationMethod = IDGenerationMethod;
        _ignorePrefix = ignorePrefix;
//            _IDGenerationQuery = IDGenerationQuery;
    }

    protected String _name;                // name of the database vendor
    protected String _jdbcTag;             // jdbc tag of the database vendor
    protected String[] _drivers;           // list of driver classes
    protected String _pingQuery;           // ping SQL query
    protected String _caseSensivity;       // case-sensivity
    protected String _schemaQuery;         // SQL query to set the current schema
    protected String _IDGenerationMethod;  // ID generation method
    protected String _ignorePrefix;
// not yet implemented (TODO)
//        public String _IDGenerationQuery;   // ID generation query

    public static void addDriver(String name,String jdbcTag,String drivers[],String pingQuery,String caseSensivity,String schemaQuery,String IDGenerationMethod,String ignorePrefix/*,String IDGenerationQuery*/)
    {
        DriverInfo infos = new DriverInfo(name,jdbcTag,drivers,pingQuery,caseSensivity,schemaQuery,IDGenerationMethod,ignorePrefix/*,IDGenerationQuery*/);
        sDriverByVendor.put(jdbcTag,infos);
        for(String clazz:drivers) {
            sDriverByClass.put(clazz,infos);
        }
    }

    /* map jdbctag -> driver infos */
    static private Map<String,DriverInfo> sDriverByVendor = new HashMap<String,DriverInfo>();

    /* map driver class -> driver infos */
    static private Map<String,DriverInfo> sDriverByClass = new HashMap<String,DriverInfo>();

    public String getJdbcTag() {
        return _jdbcTag;
    }

    protected String[] getDrivers() {
        return _drivers;
    }

    public String getPingQuery() {
        return _pingQuery;
    }

    public String getCaseSensivity() {
        return _caseSensivity;
    }

    public String getSchemaQuery() {
        return _schemaQuery;
    }

    public long getLastInsertId(Statement statement)
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
        } else if ("hsqldb".equalsIgnoreCase(getJdbcTag())) {
            /* HSQLDB */
            try  {
                ResultSet rs = statement.executeQuery("IDENTITY()");
                rs.next();
                ret = rs.getLong(1);
            } catch(Throwable e) {
                Logger.log("Could not find last insert id: ",e);
            }
        }
        else Logger.error("getLastInsertID is not [yet] implemented for your dbms... Contribute!");
        return ret;
    }

    public boolean ignoreTable(String name) {
        return _ignorePrefix != null && name.startsWith(_ignorePrefix);
    }

    // sources :
    // http://www.schemaresearch.com/products/srtransport/doc/modules/jdbcconf.html
    // http://db.apache.org/torque/ and org.apache.torque.adapter classes
    // and Google of course
    static {
        addDriver("Axion","axiondb",new String[] {"org.axiondb.jdbc.AxionDriver"},"select 1","TODO","TODO","none",null);
        addDriver("Cloudscape","cloudscape",new String[] {"COM.cloudscape.core.JDBCDriver"},"select 1","TODO","TODO","autoincrement",null);
        addDriver("DB2","db2",new String[] {"COM.ibm.db2.jdbc.app.DB2Driver","COM.ibm.db2.jdbc.net.DB2Driver"},"select 1","TODO","TODO","none",null);
        addDriver("Derby", "derby", new String[] {"org.apache.derby.jdbc.EmbeddedDriver"}, "values 1", "uppercase", "set schema $schema", "autoincrement",null);
        addDriver("Easysoft","easysoft",new String[] {"easysoft.sql.jobDriver"},"select 1","TODO","TODO","TODO",null);
        addDriver("Frontbase","frontbase",new String[] {"jdbc.FrontBase.FBJDriver"},"select 1","TODO","TODO","TODO",null);
        addDriver("HSQLDB","hsqldb",new String[] {"org.hsqldb.jdbcDriver","org.hsql.jdbcDriver"},"call 1","uppercase","set schema $schema","autoincrement","SYSTEM_");
        addDriver("Hypersonic","hypersonic",new String[] {"org.hsql.jdbcDriver"},"select 1","TODO","TODO","autoincrement",null);
        addDriver("OpenBase","openbase",new String[] {"com.openbase.jdbc.ObDriver"},"select 1","TODO","TODO","TODO",null);
        addDriver("Informix","informix",new String[] {"com.informix.jdbc.IfxDriver"},"select 1","TODO","TODO","none",null);
        addDriver("InstantDB","instantdb",new String[] {"org.enhydra.instantdb.jdbc.idbDriver"},"select 1","TODO","TODO","none",null);
        addDriver("Interbase","interbase",new String[] {"interbase.interclient.Driver"},"select 1","TODO","TODO","none",null);
        addDriver("ODBC","odbc",new String[] {"sun.jdbc.odbc.JdbcOdbcDriver"},"select 1","TODO","TODO","TODO",null);
        addDriver("Sql Server","sqlserver",new String[] {"com.microsoft.jdbc.sqlserver.SQLServerDriver","com.jnetdirect.jsql.JSQLDriver","com.merant.datadirect.jdbc.sqlserver.SQLServerDriver"},"select 1","TODO","TODO","autoincrement",null);
        addDriver("MySql","mysql",new String[] {"com.mysql.jdbc.Driver","org.gjt.mm.mysql.Driver"},"select 1","sensitive",null,"autoincrement",null);
        addDriver("OpenBase","",new String[] {"com.openbase.jdbc.ObDriver"},"select 1","TODO","TODO","TODO",null);
        addDriver("Oracle","oracle",new String[] {"oracle.jdbc.driver.OracleDriver"},"select 1 from dual","uppercase","alter session set current_schema = $schema","sequence",null);
        addDriver("PostgreSQL","postgresql",new String[] {"org.postgresql.Driver"},"select 1","lowercase",null,"autoincrement",null); // also sequences, but support for autoincrement is better
        addDriver("SapDB","sapdb",new String[] {"com.sap.dbtech.jdbc.DriverSapDB"},"select 1 from dual","uppercase","TODO","sequence",null);
        addDriver("Sybase","sybase",new String[] {"com.sybase.jdbc2.jdbc.SybDriver"},"select 1","TODO","TODO","autoincrement",null);
        addDriver("Weblogic","weblogic",new String[] {"weblogic.jdbc.pool.Driver"},"select 1","TODO","TODO","none",null);

        // unknwon driver
        addDriver("Unknown driver","unknown",new String[]{},"select 1","sensitive",null,"none",null);
    }
}

/* old implementation
    // array containing { vendor(as it appear in the url), driver-1, ... driver-n, CheckQuery/null, case-sensivity }
    // sources :
    // http://www.schemaresearch.com/products/srtransport/doc/modules/jdbcconf.html
    // http://db.apache.org/torque/ and org.apache.torque.adapter classes
    // and Google of course
    private static String sVendors[][] =
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
