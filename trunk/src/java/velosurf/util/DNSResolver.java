package velosurf.util;

import java.util.List;
import java.util.Hashtable;
import java.util.ArrayList;

import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.Attributes;
import javax.naming.directory.Attribute;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;

/**
 * Utility class to resolve names against DN servers
 */
public class DNSResolver {

    /**
     * check DNS.
     * @param hostname hostname
     * @return true if valid
     */
    public static boolean checkDNS(String hostname) {
        return checkDNS(hostname,false);
    }

    /**
     * check DNS.
     * @param hostname hostname
     * @param mx do MX query or not
     * @return true if valid
     */
    public static boolean checkDNS(String hostname,boolean mx) {
        List<String> records = resolveDNS(hostname,mx);
        return records != null && records.size() > 0;
    }

    /**
     * Resolve MX DNS.
     * @param hostname hostname
     * @return list of MXs
     */
    public static List<String> resolveDNS(String hostname,boolean mx) {
        List<String> result = new ArrayList<String>();
        try {
            Logger.trace("email validation: resolving MX DNS for "+hostname);
            Hashtable env = new Hashtable();
            env.put("java.naming.factory.initial",
                    "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", "3000"); /* quite short... too short? */
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            DirContext ictx = new InitialDirContext( env );
            String[] ids = ( mx ? new String [] { "MX" } : new String [] { "A","CNAME"} );
            Attributes attrs = ictx.getAttributes(hostname, ids);
            if (mx) {
                Attribute attr = attrs.get( "MX" );
                if (attr != null && attr.size() > 0) {
                    NamingEnumeration e = attr.getAll();
                    while(e.hasMore()) {
                        String mxs = (String)e.next();
                        String f[] = mxs.split( "\\s+" );
                        for (int i=0;i<f.length;i++) {
                            if (f[i].endsWith(".")) {
                                result.add(f[i].substring(0,f[i].length()-1));
                            }
                        }
                    }
                    return result;
                } else {
                    Logger.trace("email validation: DNS query of '"+hostname+"' failed");
                    return null;
                }
            } else {
                Attribute attr = attrs.get("A");
                if (attr != null && attr.size() > 0) {
                    NamingEnumeration e = attr.getAll();
                    while(e.hasMore()) {
                        result.add((String)e.next());
                    }
                    return result;
                } else {
                    attr = attrs.get("CNAME");
                    if (attr != null && attr.size() > 0) {
                        NamingEnumeration e = attr.getAll();
                        while(e.hasMore()) {
                            String h = (String)e.next();
                            if(h.endsWith(".")) {
                                h = h.substring(0,h.lastIndexOf('.'));
                            }
                            result.addAll(resolveDNS(h,false));
                        }
                        return result;
                    } else {
                        Logger.trace("email validation: DNS query of '"+hostname+"' failed");
                        return null;
                    }
                }
            }
        } catch(NamingException ne) {
            Logger.trace("email validation: DNS MX query failed: "+ne.getMessage());
            return null;
        }
    }
}
