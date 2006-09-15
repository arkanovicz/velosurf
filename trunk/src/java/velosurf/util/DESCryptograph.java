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

package velosurf.util;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.IllegalBlockSizeException;
import javax.crypto.KeyGenerator;
import java.io.UnsupportedEncodingException;
import java.io.IOException;
import java.security.SecureRandom;
import java.security.Security;

/**
 * inspired from some code found at http://javaalmanac.com/
 */
public class DESCryptograph implements Cryptograph {
    Cipher ecipher;
    Cipher dcipher;

    public DESCryptograph() {

    }

    public void init(String random) {
        try {
			// this is the only method that gives us reproducibility
            SecureRandom seed = SecureRandom.getInstance("SHA1PRNG");
            seed.setSeed(random.getBytes());
            KeyGenerator keygen = KeyGenerator.getInstance("DES");
            keygen.init(seed);
            SecretKey key = keygen.generateKey();

            ecipher = Cipher.getInstance("DES");
            dcipher = Cipher.getInstance("DES");
            ecipher.init(Cipher.ENCRYPT_MODE, key);
            dcipher.init(Cipher.DECRYPT_MODE, key);

        } catch (javax.crypto.NoSuchPaddingException e) {
            e.printStackTrace();
        } catch (java.security.NoSuchAlgorithmException e) {
            e.printStackTrace();
        } catch (java.security.InvalidKeyException e) {
            e.printStackTrace();
        }
    }

    public String encrypt(String str) {
        try {
            // Encode the string into bytes using utf-8
            byte[] utf8 = str.getBytes("UTF8");

            // Encrypt
            byte[] enc = ecipher.doFinal(utf8);

            // Encode bytes to base64 to get a string
            return new sun.misc.BASE64Encoder().encode(enc);
        } catch (javax.crypto.BadPaddingException e) {
            e.printStackTrace();
        } catch (IllegalBlockSizeException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public String decrypt(String str) {
        try {
            // Decode base64 to get bytes
            byte[] dec = new sun.misc.BASE64Decoder().decodeBuffer(str);

            // Decrypt
            byte[] utf8 = dcipher.doFinal(dec);

            // Decode using utf-8
            return new String(utf8, "UTF8");
        } catch (javax.crypto.BadPaddingException e) {
        } catch (IllegalBlockSizeException e) {
        } catch (UnsupportedEncodingException e) {
        } catch (java.io.IOException e) {
        }
        return null;
    }

    static
    {
        Security.addProvider(new com.sun.crypto.provider.SunJCE());
    }

   public static void main(String args[])
   {
       DESCryptograph crypt = new DESCryptograph();
       crypt.init("hello there!");
       while (true) {
           try {
               StringBuffer rst=new StringBuffer();
               int c;
               while ((c=System.in.read())!=0x0A)
               {
                   if (c!=0x0D) rst.append((char)c);
               }
               String text = rst.toString();
               System.out.println("text      -> <"+rst);
               String enc = crypt.encrypt(text);
               System.out.println("encrypted -> <"+enc+">");
               enc = enc.replace('=','.');
               enc = enc.replace('/','_');
               enc = enc.replace('+','*');
               System.out.println("encoded -> <"+enc+">");
               String dec = enc;
               dec = dec.replace('.','=');
               dec = dec.replace('_','/');
               dec = dec.replace('*','+');
               System.out.println("decoded -> <"+dec+">");
               System.out.println("decrypted -> <"+crypt.decrypt(dec)+">");
           }
           catch (IOException ioe) {
               Logger.log(ioe);
           }
       }
   }
}
