package velosurf.util;

import java.io.Serializable;

/**
 * Cryptograph - used to encrypt and decrypt strings.
 *
 *  @author <a href=mailto:claude.brisson@gmail.com>Claude Brisson</a>
 */
public interface Cryptograph extends Serializable
{
    /**
     * init.
     * @param random random string
     */
    public void init(String random);

    /**
     * encrypt.
     * @param str string to encrypt
     * @return encrypted string
     */
    public String encrypt(String str);

    /**
     * decrypt.
     * @param str string to decrypt
     * @return decrypted string
     */
    public String decrypt(String str);
}
