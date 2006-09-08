package velosurf.util;

/**
 * Created by IntelliJ IDEA.
 * User: claude
 * Date: 8 juin 2004
 * Time: 20:57:08
 * To change this template use Options | File Templates.
 */
public interface Cryptograph {
    public void init(String random);
    public String encrypt(String str);
    public String decrypt(String str);
}
