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
import java.util.List;
import java.net.Socket;
import java.net.UnknownHostException;
import java.io.IOException;
import java.io.PrintStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import velosurf.util.Logger;
import velosurf.util.DNSResolver;

/**
 * <p>An 'email' constraint. Syntax is:</p>
 *
 *  <pre>
 *    &lt;<i>column</i> type="email"/&gt;
 *  </pre>
 * <p>Or:</p>
 *   <pre>
 *     &lt;<i>column</i>&gt;
 *       &lt;email [dns-check="true | <b>false</b>"] [smtp-check="true | <b>false</b>" ] [message="<i>error-message</i>"]&gt;
 *     &lt;/<i>column</i>&gt;
 *   </pre>
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */
public class Email extends FieldConstraint {
    /** whether to check dns. */
    private boolean dnsCheck = false;
    /** whether to check smtp server. */
    private boolean smtpCheck = false;
    /** valid email pattern. */
    private static Pattern validEmail = null;

    static {
        /* Do we really want to allow all those strange characters in emails?
           Well, that's what the RFC2822 seems to allow... */
        String atom = "[a-zA-Z0-9!#$%&'*+-/=?^_`{|}~]";
        String domain = "(?:[a-zA-Z0-9](?:[-a-zA-Z0-9]*[a-zA-Z0-9]+)?)";
        validEmail = Pattern.compile(
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
        this.dnsCheck = dnsCheck;
        this.smtpCheck = smtpCheck;
        setMessage("field {0}: '{1}' is not a valid email");
    }

    /**
     * Validate data against this constraint.
     * @param data data to validate
     * @return whether data is valid
     */
    public boolean validate(Object data) {
        if (data == null || data.toString().length() == 0) return false; // should be true if NULL allowed, but stastistically email columns are often mandatory
        Matcher matcher = validEmail.matcher(data.toString());
        if (!matcher.matches()) {
            return false;
        }
        String user = matcher.group(1);
        String hostname = matcher.group(2);
        /* first, DNS validation */
        if (dnsCheck && !DNSResolver.checkDNS(hostname,true)) {
            return false;
        }
        /* then, SMTP */
        if(smtpCheck && !checkSMTP(user,hostname)) {
            return false;
        }
        return true;
    }

    /**
     * Check SMTP server.
     * @param user username
     * @param hostname hostname
     * @return true if valid
     */
    private boolean checkSMTP(String user,String hostname) {
        String response;
        Socket sock = null;
        Logger.trace("email validation: checking SMTP for <"+user+"@"+hostname+">");
        List<String> mxs = DNSResolver.resolveDNS(hostname,true);
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

    /**
     * A socket with short timeout.
     */
    class FastTimeoutConnect implements Runnable {
        /** host. */
        private String host;
        /** port. */
        private int port;
        /** connection successfull? */
        private boolean done = false;
        /** timeout. */
        private int timeout;
        /** wrapped socket. */
        private Socket socket = null;
        /** thrown I/O exception. */
        private IOException ioe;
        /** throws unknown host exception. */
        private UnknownHostException uhe;

        /**
         * Constructor.
         * @param h host
         * @param p port
         * @param t timeout
         */
        public FastTimeoutConnect(String h,int p,int t) {
            host = h;
            port = p;
            timeout = t;
        }

        /**
         * Connect.
         * @return socket
         * @throws IOException
         * @throws UnknownHostException
         */
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

        /**
         * connection process.
         */
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

    /**
     * return a string representation for this constraint.
     * @return string
     */
    public String toString() {
        return "type email, check-dns="+dnsCheck+", check-smtp="+smtpCheck;

    }

}
