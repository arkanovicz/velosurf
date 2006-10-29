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

package velosurf.validation;

import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.Hashtable;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.net.Socket;
import java.net.UnknownHostException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.DirContext;
import javax.naming.NamingException;
import javax.naming.NamingEnumeration;

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
        String atom = "[a-z0-9!#$%&'*+-/=?^_`{|}~]";
        String domain = "(?:[a-z0-9](?:[-a-z0-9]*[a-z0-9]+)?)";
        _validEmail = Pattern.compile(
                "(^" + atom + "+" + "(?:\\."+atom+")*)" +
                "@((?:" + domain + "{1,63}\\.)+" + domain + "{2,63})$",Pattern.CASE_INSENSITIVE
        );
    }

    /**
     * Default constructor.
     */
    public Email() {
        this(false,false);
    }

    /**
     * Constructor.
     * @param dnsCheck whether to validate this email using a DNS query
     * @param smtpCheck whether to validate this email using an STMP query
     */
    public Email(boolean dnsCheck,boolean smtpCheck) {
        _dnsCheck = dnsCheck;
        _smtpCheck = smtpCheck;
        setMessage("field {0}: '{1}' is not a valid email");
    }

    /**
     *
     * @param data the data to be validated
     * @return true if data matches the regex pattern
     */
    public boolean validate(Object data) {
        if (data == null || data.toString().length() == 0) return true;
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
        List<String> mxs = resolveMXDNS(hostname);
        return mxs != null && mxs.size() > 0;
    }

    /* TODO: move it in a helper class in velosurf.util "*/
    private List<String> resolveMXDNS(String hostname) {
        try {
            Logger.trace("email validation: resolving MX DNS for "+hostname);
            Hashtable env = new Hashtable();
            env.put("java.naming.factory.initial",
                    "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", "3000"); /* quite short... too short? */
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            DirContext ictx = new InitialDirContext( env );
            Attributes attrs = ictx.getAttributes(hostname, new String[] { "MX" });
            Attribute attr = attrs.get( "MX" );
            if (attr != null && attr.size() > 0) {
                List<String> result = new ArrayList<String>();
                NamingEnumeration e = attr.getAll();
                while(e.hasMore()) {
                    String mx = (String)e.next();
                    String f[] = mx.split( "\\s+" );
                    for (int i=0;i<f.length;i++) {
                        if (f[i].endsWith(".")) {
                            result.add(f[i].substring(0,f[i].length()-1));
                        }
                    }
                }
                return result;
            } else {
                Logger.trace("email validation: DNS MX query failed");
                return null;
            }
        } catch(NamingException ne) {
            Logger.trace("email validation: DNS MX query failed: "+ne.getMessage());
            return null;
        }

    }

    private boolean checkSMTP(String user,String hostname) {
        String response;
        Socket sock = null;
        Logger.trace("email validation: checking SMTP for <"+user+"@"+hostname+">");
        List<String> mxs = resolveMXDNS(hostname);
        if (mxs == null || mxs.size() == 0) {
            return false;
        }
        for(String mx:mxs) {
            try {
                Logger.trace("email validation: checking SMTP: trying with MX server "+mx);
                sock = new FastTimeoutConnect(mx,25,3000).connect();
                if (sock == null) {
                    Logger.trace("email validation: checking SMTP: timeout");
                    continue;
                }
                BufferedReader is = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                PrintStream os = new PrintStream(sock.getOutputStream());
                response = is.readLine();
                Logger.trace("  "+response);
                if(!response.startsWith("220 ")) {
                    Logger.trace("email validation: checking SMTP: failed after connection");
                    if(response.startsWith("4")) {
                        /* server has problems */
                        continue;
                    }
                    else {
                        return false;
                    }
                };
                os.println("HELO email-checker@localhost.foo");
                response = is.readLine();
                Logger.trace("  "+response);
                if(!response.startsWith("250 ")) {
                    Logger.trace("email validation: checking SMTP: failed after HELO");
                    if(response.startsWith("4")) {
                        /* server has problems */
                        continue;
                    }
                    else {
                        return false;
                    }
                };
                /* note that if the mail server issues a premature DNS check, the process may fail
                   for valid emails */
                os.println("MAIL FROM:<email-checker@localhost.foo>");
                response = is.readLine();
                Logger.trace("  "+response);
                if(!response.startsWith("250 ")) {
                    Logger.trace("email validation: checking SMTP: failed after MAIL FROM");
                    if(response.startsWith("4")) {
                        /* server has problems */
                        continue;
                    }
                    else {
                        return false;
                    }
                };
                os.println("RCPT TO:<"+user+"@"+hostname+">");
                response = is.readLine();
                Logger.trace("  "+response);
                if(!response.startsWith("250 ")) {
                    Logger.trace("email validation: checking SMTP: failed after RCPT TO");
                    if(response.startsWith("4")) {
                        /* server has problems */
                        continue;
                    }
                    else {
                        return false;
                    }
                };
                try {
                    os.println("QUIT");
                } catch(Exception e) {}
                Logger.trace("email validation: checking SMTP: success");
                return true;
            } catch(Exception e) {
                Logger.trace("email validation: checking SMTP: failure with exception: "+e.getMessage());
            }
            finally {
                if (sock != null && !sock.isClosed()) {
                    try {
                        sock.close();
                    } catch (IOException ioe) {}
                }
            }
        }
        Logger.trace("email validation: checking SMTP: failure for all MXs");
        return false;
    }

    class FastTimeoutConnect implements Runnable {

        private String host;
        private int port;
        private boolean done = false;
        private int timeout;
        private Socket socket = null;
        private IOException ioe;
        private UnknownHostException uhe;

        public FastTimeoutConnect(String h,int p,int t) {
            host = h;
            port = p;
            timeout = t;
        }

        public Socket connect() throws IOException, UnknownHostException {
            int waited=0;
            Thread thread = new Thread(this);
            thread.start();
            while(!done && waited < timeout) {
                    try {
                            Thread.sleep(100);
                            waited+=100;
                    } catch(InterruptedException e) {
                            throw new IOException("sleep interrupted");
                    }
            }
            if (!done)
                    thread.interrupt();
            if (ioe != null)
                    throw ioe;
            if (uhe != null)
                    throw uhe;
            return socket;
        }


        public void run() {
            try {
                socket = new Socket(host, port);
            } catch(UnknownHostException uhe) {
                this.uhe = uhe;
            } catch(IOException ioe) {
                this.ioe = ioe;
            } finally {
                done = true;
            }
        }
    }
}
