package velosurf.util;

/**
 * Cryptograph - used to encrypt and decrypt strings
 *
 *  @author <a href=mailto:claude.brisson.com>Claude Brisson</a>
 */
public interface Cryptograph {
    public void init(String random);
    public String encrypt(String str);
    public String decrypt(String str);
}
