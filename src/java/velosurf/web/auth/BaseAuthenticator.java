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

package velosurf.web.auth;

import javax.servlet.http.HttpSession;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import java.util.Random;
import java.util.Map;
import java.math.BigInteger;
import java.security.NoSuchAlgorithmException;
import java.security.Key;
import java.security.InvalidKeyException;
import java.io.UnsupportedEncodingException;

import org.apache.velocity.tools.view.context.ViewContext;

import velosurf.util.Logger;

import sun.misc.BASE64Encoder;
import sun.misc.BASE64Decoder;

/**
 * This abstract class implements an authentication mechanism. It is meant to be declared
 * in toolbox.xml as a session-scoped tool.
 *
 * You will need to implement the same password encryption on the client side using the adequate
 * javascript files.
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */

public abstract class BaseAuthenticator {

    protected abstract String getPassword(String login);
    protected abstract Object getUser(String login);

    private String method = null;
    private String challenge = null;
    private static Random _random = new Random(System.currentTimeMillis());

    private static final int CHALLENGE_LENGTH = 256; // bits

    public void init(Object initData) {
        if (!(initData instanceof ViewContext)) {
            Logger.error("auth: authenticator tool should be used in a session scope!");
        }
    }

    public void configure(Map config) {
        method = (String)config.get("method");
        Logger.debug("auth: using method "+method);
    }

    /**
     * This method generates a new challenge each time it is called.
     *
     * @return a new 1024-bit challenge in base64
     */
    public String getChallenge() {
        BigInteger bigint = new BigInteger(CHALLENGE_LENGTH,_random);
        challenge = new sun.misc.BASE64Encoder().encode(bigint.toByteArray());
        challenge = challenge.replace("\n","");
        Logger.trace("auth: challenge="+challenge);
        return challenge;
    }

    public boolean checkLogin(String login,String answer) {
        String password = getPassword(login);
        if(password == null) {
            /* password not found */
            return false;
        }
        String correctAnswer = generateAnswer(password);
        Logger.trace("auth: received="+answer);
        Logger.trace("auth: correct ="+correctAnswer);
        return (correctAnswer != null && correctAnswer.equals(answer));
    }

    private String generateAnswer(String password) {
        if(method == null) {
            return password;
        } else {
            try {
                /* TODO: use utf8 (and find a way to convert an utf8 string into
                   an array of bytes on the javascript counterpart) */
                Mac mac = Mac.getInstance(method);
                mac.init(new SecretKeySpec(password.getBytes("ISO-8859-1"),method));
                byte[] hash = mac.doFinal(challenge.getBytes("ISO-8859-1"));
                String encoded = new BASE64Encoder().encode(hash);
                /* strips the last(s) '=' */
                int i;
                while((i=encoded.lastIndexOf('='))!=-1) {
                    encoded = encoded.substring(0,i);
                }
                return encoded;
            } catch(NoSuchAlgorithmException nsae) {
                Logger.error("auth: could not find algorithm '"+method+"'");
                Logger.log(nsae);
            } catch(Exception e) {
                Logger.error("auth: an unknown error occurred...");
                Logger.log(e);
            }
        }
        return null;
    }
}
