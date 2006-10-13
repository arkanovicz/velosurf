package blackbox;

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
}
