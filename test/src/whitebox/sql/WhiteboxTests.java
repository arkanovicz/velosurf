package whitebox.sql;

import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.io.PrintWriter;

import org.junit.*;
import static org.junit.Assert.*;

import velosurf.sql.Database;
import velosurf.sql.PooledPreparedStatement;
import velosurf.context.RowIterator;
import velosurf.context.Instance;
import velosurf.model.Entity;
import velosurf.model.Transaction;
import velosurf.model.Attribute;
import velosurf.util.Logger;
import velosurf.util.UserContext;

public class WhiteboxTests
{
    protected static final String MODEL_FILE = "conf/model.xml";

    protected static Database database = null;

	public static @BeforeClass void openDatabase() throws Exception {
        Logger.setWriter(new PrintWriter("log/whitebox.log"));
        database = Database.getInstance(MODEL_FILE);
    }

    public @Test void testQuery() throws SQLException {
        RowIterator iterator = database.query("select * from publisher order by publisher_id");
        assertTrue(iterator.hasNext());
        Object row = iterator.next();
        assertFalse(iterator.hasNext());
        assertNotNull(row);
        assertTrue(row instanceof Instance);
        Instance i = (Instance)row;
for(Object k:i.keySet()){Logger.debug("### "+k+" -> "+i.get(k));}        
        assertTrue(i.get("publisher_id") != null);
        assertTrue(i.get("name") != null);
    }

    public @Test void testQuery2() throws SQLException {
        Entity book = database.getEntity("book");
        assertNotNull(book);
        RowIterator iterator = book.query();
        assertTrue(iterator.hasNext());
        Object row = iterator.next();
        assertNotNull(row);
        assertTrue(row instanceof Instance);
        Instance instance = (Instance)row;
        assertTrue(instance.getEntity() != null);
        assertTrue(instance.get("book_id") != null);
        assertTrue(instance.get("title") != null);
        assertTrue(instance.get("isbn") != null);
        assertTrue(instance.get("publisher_id") != null);
        assertTrue(instance.get("author_id") != null);
        assertTrue(iterator.hasNext());
        row = iterator.next();
        assertNotNull(row);
        instance = (Instance)row;
        assertTrue(instance.getEntity() != null);
        assertTrue(instance.get("book_id") != null);
        assertTrue(instance.get("title") != null);
        assertTrue(instance.get("isbn") != null);
        assertTrue(instance.get("publisher_id") != null);
        assertTrue(instance.get("author_id") != null);
        assertFalse(iterator.hasNext());
        row = iterator.next();
        assertNull(row);
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

    public @Test void testEmptyTable() throws SQLException {
        Entity empty = database.getEntity("empty");
        assertNotNull(empty);
        RowIterator iterator = empty.query();
        assertFalse(iterator.hasNext());
        Object row = iterator.next();
        assertNull(row);
    }

    public @Test void testInsert() throws SQLException {
        UserContext ctx = new UserContext();
        database.setUserContext(ctx);
        Entity user = database.getEntity("user");
        assertNotNull(user);
        Map<String,Object> row = new HashMap<String,Object>();
        row.put("login","donald");
        row.put("password","duck");
        assertTrue(user.insert(row));
        long id = ctx.getLastInsertedID(user);
        assertTrue(id == 2);
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
        transaction.perform(new HashMap<String,Object>());
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
            transaction.perform(new HashMap<String,Object>());
        } finally {
            Instance row = database.getEntity("publisher").fetch(201);
            assertNull(row);
        }
    }

    public @Test void testXInclude() throws SQLException {
        /* testing the included attribute */
        Attribute attribute = database.getRootEntity().getAttribute("count_publishers");
        Object result = attribute.evaluate(null);
        assertEquals("java.lang.Integer",result.getClass().getName());
        assertEquals(1,result);
    }

    public static @AfterClass void closeDatabase() throws SQLException {
        database.close();
    }

}

