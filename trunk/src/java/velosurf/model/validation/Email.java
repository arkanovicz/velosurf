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

package velosurf.model.validation;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Hashtable;
import java.net.Socket;
import java.io.IOException;
import java.io.PrintStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.DirContext;
import javax.naming.NamingException;

import velosurf.util.Logger;

/**
 * <p>An 'email' constraint. Syntax is:</p>
 *
 *  <pre>
 *    &lt;<i>column</i> type="email"/&gt;
 *  </pre>
 *<p>(Alas, unvalued attributes are not valid in XML...)<br>Or:</p>
 *   <pre>
 *     &lt;<i>column</i>&gt;
 *       &lt;email [dns-check="true | <b>false</b>"] [smtp-check="true | <b>false</b>" ] [message="<i>error-message</i>"]&gt;
 *     &lt;/<i>column</i>&gt;
 *   </pre>
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */
public class Email extends FieldConstraint {

    private boolean _dnsCheck = false;

    private boolean _smtpCheck = false;

    private static Pattern _validEmail = null;

    static {
        /* Do we really want to allow all those strange characters in emails?
           Well, that's what the RFC2822 seems to allow... */
        String atom = "[-a-z0-9!#$%&\\'*+\\\\/=?^_`{|}~]";
        String domain = "(?:[a-z0-9](?:[-a-z0-9]*[a-z0-9]+)?)";
        _validEmail = Pattern.compile(
                "(/^" + atom + "+" + "(?:\\."+atom+")*)" +
                "@((?:" + domain + "{1,63}\\.)+" + domain + "{2,63})$",Pattern.CASE_INSENSITIVE
        );
    }

    /**
     * Default constructor.
     */
    public Email() {
        setMessage("is not an email");
    }

    /**
     * Constructor.
     * @param dnsCheck whether to validate this email using a DNS query
     * @param smtpCheck whether to validate this email using an STMP query
     */
    public Email(boolean dnsCheck,boolean smtpCheck) {
        _dnsCheck = dnsCheck;
        _smtpCheck = smtpCheck;
        setMessage("is not an email");
    }

    /**
     *
     * @param data the data to be validated
     * @return true if data matches the regex pattern
     */
    public boolean validate(Object data) {
        Matcher matcher = _validEmail.matcher(data.toString());
        if (!matcher.matches()) {
            return false;
        }
        String user = matcher.group(1);
        String hostname = matcher.group(2);
        /* first, DNS validation */
        if (_dnsCheck && !checkDNS(hostname)) {
            return false;
        }
        /* then, SMTP */
        if(_smtpCheck && !checkSMTP(user,hostname)) {
            return false;
        }
        return true;
    }

    private boolean checkDNS(String hostname) {
        try {
            Hashtable env = new Hashtable();
            env.put("java.naming.factory.initial",
                    "com.sun.jndi.dns.DnsContextFactory");
            DirContext ictx = new InitialDirContext( env );
            Attributes attrs = ictx.getAttributes(hostname, new String[] { "MX" });
            Attribute attr = attrs.get( "MX" );
            return attr != null && attr.size() > 0 ;
        } catch(NamingException ne) {
            Logger.log(ne);
            return false;
        }
    }

    private boolean checkSMTP(String user,String hostname) {
        Socket sock = null;
        String response;
        try {
            sock = new Socket(hostname,25);
            BufferedReader is = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            PrintStream os = new PrintStream(sock.getOutputStream());
            response = is.readLine();
            if(!response.startsWith("220 ")) {
                Logger.trace("checkSMTP: failed after connection: response="+response);
                return false;
            };
            os.println("HELO email-checker@localhost.foo");
            response = is.readLine();
            if(!response.startsWith("250 ")) {
                Logger.trace("checkSMTP: failed after HELO: response="+response);
                return false;
            };
            /* note that if the mail server issues a premature DNS check, the process may fail
               for valid emails */
            os.println("MAIL FROM:<email-checker@localhost.foo>");
            response = is.readLine();
            if(!response.startsWith("250 ")) {
                Logger.trace("checkSMTP: failed after MAIL FROM: response="+response);
                return false;
            };
            os.println("RCPT TO:<"+user+"@"+hostname+">");
            response = is.readLine();
            if(!response.startsWith("250 ")) {
                Logger.trace("checkSMTP: failed after RCPT TO: response="+response);
                return false;
            };
            try {
                os.println("QUIT");
            } catch(Exception e) {}

        } catch(Exception e) {
            return false;
        }
        finally {
            if (sock != null && !sock.isClosed()) {
                try {
                    sock.close();
                } catch (IOException ioe) {}
            }
        }
        return true;
    }
}
