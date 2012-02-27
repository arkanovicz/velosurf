package blackbox;

import java.io.PrintWriter;
import java.util.Arrays;

import org.junit.*;
import static org.junit.Assert.*;

import com.meterware.httpunit.*;

import velosurf.util.Logger;

public class BlackboxTests
{

   /* TODO parametrize the port number with ant property ${test.webcontainer.port}
      (requires a filtered copy of the source files) */
   public static final int SERVER_PORT = 8081;

    private void checkText(WebResponse resp,String id,String text) throws Exception {
        HTMLElement element = resp.getElementWithID(id);
        assertNotNull(element);
        assertEquals(text,element.getText());
    }

    public @BeforeClass static void initLogger() throws Exception {
        Logger.setWriter(new PrintWriter("log/blackbox.log"));
    }

	public @Test void basicTests() throws Exception {
        WebConversation wc = new WebConversation();
        WebRequest     req = new GetMethodWebRequest( "http://localhost:"+SERVER_PORT+"/basic.html" );
        WebResponse   resp = wc.getResponse( req );
        checkText(resp,"test1","Addison Wesley Professional");
        checkText(resp,"test2.1","Effective Java");
        checkText(resp,"test2.2","TCP/IP Illustrated, Volume 1");
        assertNull(resp.getElementWithID("test2.3"));
        checkText(resp,"test3","Effective Java");
        checkText(resp,"test4","Joshua Bloch");
    }

    public @Test void paramFetching() throws Exception {
        WebConversation wc = new WebConversation();
        WebRequest     req = new GetMethodWebRequest( "http://localhost:"+SERVER_PORT+"/obfuscate.html?id=1" );
        WebResponse   resp = wc.getResponse( req );
        String obfId = resp.getElementWithID("obfuscated").getText();
        req = new GetMethodWebRequest( "http://localhost:"+SERVER_PORT+"/fetch.html?publisher_id="+obfId+"&book_id="+obfId);
        resp = wc.getResponse( req );
        checkText(resp,"publisher","Addison Wesley Professional");
        checkText(resp,"book","Effective Java");
    }

    public @Test void authentication() throws Exception {
        /* try to reach a protected resource */
        WebConversation wc = new WebConversation();
        WebRequest req = new GetMethodWebRequest("http://localhost:"+SERVER_PORT+"/auth/protected.html");
        WebResponse resp = wc.getResponse(req);
        assertEquals("Login",resp.getTitle());
        /* log in */
        WebForm form = resp.getFormWithName("login");
        form.setParameter("login","foo");
        form.setParameter("password","bar");
        resp = form.submit();
        /* check we reach the initially requested resource */
        assertEquals("protected",resp.getTitle());
        /* log out */
        WebLink logout = resp.getLinkWith("logout");
        assertNotNull(logout);
        resp = logout.click();
        assertEquals("Login",resp.getTitle());
        /* bad login */
        form = resp.getFormWithName("login");
        form.setParameter("login","foo");
        form.setParameter("password","badpass");
        resp = form.submit();
        assertEquals("Login",resp.getTitle());
        String msg = resp.getElementWithID("message").getText();
        /* in english or french, may depend of the system */
        assertTrue(Arrays.asList(new String[]{"Bad login or password.","Mauvais login ou mot de passe...","badLogin"}).contains(msg));
        /* log in again, check we reach the auth index */
        form = resp.getFormWithName("login");
        form.setParameter("login","foo");
        form.setParameter("password","bar");
        resp = form.submit();
        assertEquals("Protected Index",resp.getTitle());
    }

    public @Test void validation() throws Exception {
        WebConversation wc = new WebConversation();
        WebRequest req = new GetMethodWebRequest("http://localhost:"+SERVER_PORT+"/input.html");
        WebResponse resp = wc.getResponse(req);
        assertEquals("Input form",resp.getTitle());
        /* first try with bad values */
        WebForm form = resp.getFormWithName("input");
        form.setParameter("string","aa");
        form.setParameter("string2","123-1234");
        form.setParameter("number","0");
        form.setParameter("oneof","test0");
        form.setParameter("mydate","2-7-2006");
        form.setParameter("email","toto@tata@titi");
        //form.setParameter("email2",(String)null);
        form.setParameter("book_id","0");
        resp = form.submit();
        assertEquals("Input form",resp.getTitle());
        /* check error messages */
        checkText(resp,"1","field STRING: value [aa] is not of the proper length");
        checkText(resp,"2","field STRING2: value [123-1234] is not valid");
        checkText(resp,"3","field NUMBER: [0] is not in the valid range");
        checkText(resp,"4","field ONEOF: value [test0] must be one of: test1, test2, test3");
        // checkText(resp,"5","field MYDATE: [2-7-2006] is not a valid date or is outside range"); seems broken...
        checkText(resp,"5","field EMAIL: [toto@tata@titi] is not a valid email");
        checkText(resp,"6","field EMAIL2 cannot be empty");
        checkText(resp,"7","field EMAIL2: [empty value] is not a valid email");
        checkText(resp,"8","field BOOK_ID: value [0] not found in book.book_id");
        assertNull(resp.getElementWithID("10"));
        /* check that the form retained the values */
        form = resp.getFormWithName("input");
        assertEquals("aa",form.getParameterValue("string"));
        assertEquals("123-1234",form.getParameterValue("string2"));
        assertEquals("0",form.getParameterValue("number"));
        assertEquals("test0",form.getParameterValue("oneof"));
        assertEquals("2-7-2006",form.getParameterValue("mydate"));
        assertEquals("toto@tata@titi",form.getParameterValue("email"));
        assertEquals("",form.getParameterValue("email2"));
        assertEquals("0",form.getParameterValue("book_id"));
        /* resubmit with good values excecpt for email2*/
        form.setParameter("string","aaaaaa");
        form.setParameter("string2","123-123");
        form.setParameter("number","1");
        form.setParameter("oneof","test1");
        form.setParameter("mydate","2-8-2006");
        form.setParameter("email","claude@renegat.net");
        form.setParameter("book_id","1");
        /* test DNS email checking */
        form.setParameter("email2","toto@azerty.blabla");
        resp = form.submit();
        assertEquals("Input form",resp.getTitle());
        checkText(resp,"1","field EMAIL2: [toto@azerty.blabla] is not a valid email");
        assertNull(resp.getElementWithID("2"));
        /* test SMTP email checking */
/*
        form = resp.getFormWithName("input");
        form.setParameter("email2","inexistant_email@gmail.com");
        resp = form.submit();
        assertEquals("Input form",resp.getTitle());
        checkText(resp,"1","field email2: [inexistant_email@gmail.com] is not a valid email");
        assertNull(resp.getElementWithID("2"));
*/
        /* now with a valid email... */
        form = resp.getFormWithName("input");
        form.setParameter("email2","claude.brisson@gmail.com");
        resp = form.submit();
        assertEquals("Good values!",resp.getTitle());
    }

    public @Test void testXInclude() throws Exception {
        WebConversation wc = new WebConversation();
        WebRequest req = new GetMethodWebRequest("http://localhost:"+SERVER_PORT+"/xinclude.html");
        WebResponse resp = wc.getResponse(req);
        assertEquals("XInclude",resp.getTitle());
        checkText(resp,"result","1");
    }

    public @Test void testInsert() throws Exception {
        WebConversation wc = new WebConversation();
        WebRequest req = new GetMethodWebRequest("http://localhost:"+SERVER_PORT+"/insert.html");
        WebResponse resp = wc.getResponse(req);
        checkText(resp,"result","3");
    }

    public @Test void testParametrizedGetter() throws Exception {
        WebConversation wc = new WebConversation();
        WebRequest req = new GetMethodWebRequest("http://localhost:"+SERVER_PORT+"/externalparam.html");
        WebResponse resp = wc.getResponse(req);
        checkText(resp,"test.checknbmsg","4");

    }

}
