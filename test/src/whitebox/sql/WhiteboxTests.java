package whitebox.sql;

import java.sql.SQLException;
import java.util.Map;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;

import org.junit.*;
import static org.junit.Assert.*;

import velosurf.sql.Database;
import velosurf.sql.PooledPreparedStatement;
import velosurf.sql.MapDataAccessor;
import velosurf.context.RowIterator;
import velosurf.context.Instance;
import velosurf.model.Entity;
import velosurf.model.Transaction;
import velosurf.util.Logger;

public class WhiteboxTests
{
    protected static final String MODEL_FILE = "conf/model.xml";

    protected static Database database = null;

	public static @BeforeClass void openDatabase() throws Exception {
        database = Database.getInstance(MODEL_FILE);
    }

    public @Test void testQuery() throws SQLException {
        RowIterator iterator = database.query("select * from publisher order by publisher_id");
        assertTrue(iterator.hasNext());
        Object row = iterator.next();
        assertNotNull(row);
        assertTrue(row instanceof RowIterator);
        RowIterator r = (RowIterator)row;
        assertTrue(r.get("publisher_id") != null);
        assertTrue(r.get("name") != null);
    }

    public @Test void testQuery2() throws SQLException {
        Entity publisher = database.getEntity("publisher");
        assertNotNull(publisher);
        RowIterator iterator = database.query("select * from publisher order by publisher_id",publisher);
        assertTrue(iterator.hasNext());
        Object row = iterator.next();
        assertNotNull(row);
        assertTrue(row instanceof Instance);
        Instance instance = (Instance)row;
        assertTrue(instance.getEntity() != null);
        assertTrue(instance.get("publisher_id") != null);
        assertTrue(instance.get("name") != null);
    }

    public @Test void testEvaluate() throws SQLException {
        Object scalar = database.evaluate("select count(*) from publisher");
        assertTrue(scalar instanceof Number);
    }

    public @Test void testPrepare() throws SQLException {
        PooledPreparedStatement prep = database.prepare("select * from publisher where publisher_id=?");
        Object result = prep.fetch(Arrays.asList(new String[] {"1"}),database.getEntity("publisher"));
        assertTrue(result != null && result instanceof Instance);
    }

    public @Test void testSuccessfullTransaction() throws SQLException {
        Entity root = database.getRootEntity();
        Transaction transaction = new Transaction("testSuccessfullTransaction",root);
        transaction.setQueries(
                Arrays.asList(
                        new String[] {
                            "insert into publisher (publisher_id,name) values (200,'test')",
                            "delete from publisher where publisher_id=200"
                        }
                )
        );
        List params = new ArrayList<List>();
        params.add(new ArrayList<String>());
        params.add(new ArrayList<String>());
        transaction.setParamNamesLists(params);
        transaction.perform(new MapDataAccessor(new HashMap()));
    }

    public @Test(expected=SQLException.class) void testUnsuccessfullTransaction() throws SQLException {
        try {
            Entity root = database.getRootEntity();
            Transaction transaction = new Transaction("testSuccessfullTransaction",root);
            transaction.setQueries(
                    Arrays.asList(
                            new String[] {
                                "insert into publisher (publisher_id,name) values (201,'test')",
                                "insert into publisher (publisher_id,name) values (1,'test')",
                            }
                    )
            );
            List params = new ArrayList<List>();
            params.add(new ArrayList<String>());
            params.add(new ArrayList<String>());
            transaction.setParamNamesLists(params);
            transaction.perform(new MapDataAccessor(new HashMap()));
        } finally {
            Instance row = database.getEntity("publisher").fetch(201);
            assertNull(row);
        }
    }

    public static @AfterClass void closeDatabase() throws SQLException {
        database.close();
    }

}

