package blackbox;

import java.io.PrintWriter;

import org.junit.*;
import static org.junit.Assert.*;

import com.meterware.httpunit.*;

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

    public @Test void autofetching() throws Exception {
        WebConversation wc = new WebConversation();
        WebRequest     req = new GetMethodWebRequest( "http://localhost:"+SERVER_PORT+"/obfuscate.html?id=1" );
        WebResponse   resp = wc.getResponse( req );
        String obfId = resp.getElementWithID("obfuscated").getText();
        req = new GetMethodWebRequest( "http://localhost:"+SERVER_PORT+"/autofetch.html?publisher_id="+obfId+"&book_id="+obfId);
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
        checkText(resp,"message","Bad login or password.");
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
        form.setParameter("mydate","2-8-2006");
        form.setParameter("email","toto@tata@titi");
        //form.setParameter("email2",(String)null);
        form.setParameter("book_id","0");
        resp = form.submit();
        assertEquals("Input form",resp.getTitle());

    }
}
