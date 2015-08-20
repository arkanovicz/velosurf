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

import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.math.BigInteger;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.Random;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import javax.servlet.http.HttpSession;
import org.apache.velocity.tools.view.context.ViewContext;
import sun.misc.BASE64Decoder;
import sun.misc.BASE64Encoder;
import velosurf.util.Logger;

/**
 * This abstract class implements an authentication mechanism. It is meant to be declared
 * in toolbox.xml as a session-scoped tool.
 *
 * The password encryption method can be specified in <code>toolbox.xml</code> using the <code>method</code> parameter
 * (when not specified, passwords are passed in clear).
 *
 * You will need to implement the same password encryption on the client side using the adequate
 * javascript files. A <code>/src/javascript/md5.js</code> file is provided to help implementing the HmacMD5 method.
 *
 * Still, if you really want security, use HTTPS!
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */
public abstract class BaseAuthenticator implements Serializable
{
    /**
     * get the password corresponding to a login.
     * @param login login
     * @return password or null
     */
    public abstract String getPassword(String login);

    /**
     * Get the user object corresponding to a login
     * @param login login
     * @return user object
     */
    public abstract Object getUser(String login);

    /** encryption method */
    private String method = null;

    /** challenge value */
    private String challenge = null;

    /** random number generator */
    private static Random random = new Random(System.currentTimeMillis());

    /** length of challenge */
    private static final int CHALLENGE_LENGTH = 256;    // bits

    /**
     * initialize this tool.
     * @param initData a view context
     */
    public void init(Object initData)
    {
        if(!(initData instanceof ViewContext))
        {
            Logger.error("auth: authenticator tool should be used in a session scope! (received init data of class: "
                         + (initData == null ? "null" : initData.getClass().getName()) + ")");
        }

        HttpSession s = ((ViewContext)initData).getRequest().getSession(true);
        s.setAttribute(BaseAuthenticator.class.getName(), this);
    }

    /**
     * configure this tool.
     * @param config map containing an optional "method" parameter
     */
    public void configure(Map config)
    {
        method = (String)config.get("method");
    }

    /**
     * This method generates a new challenge if needed.
     *
     * @return a 1024-bit challenge in base64
     */
    public String getChallenge()
    {
        if (challenge == null)
			  {
            BigInteger bigint = new BigInteger(CHALLENGE_LENGTH, random);
            challenge = new sun.misc.BASE64Encoder().encode(bigint.toByteArray());
            challenge = challenge.replace("\n", "");
            Logger.trace("auth: generated new challenge: " + challenge);
				}
        return challenge;
    }

    /**
     * Check received answer.
     *
     * @param login  login
     * @param answer received answer
     * @return true if received answer is valid
     */
    public boolean checkLogin(String login, String answer)
    {
        String password = getPassword(login);

        if(password == null)
        {
            /* password not found */
            Logger.trace("auth: login " + login + " does not exist");
            return false;
        }
        if(password.length() == 0 && answer.length() == 0)
        {
            return true;
        }

        String correctAnswer = generateAnswer(password);

        Logger.trace("auth: received=" + answer);
        Logger.trace("auth: correct =" + correctAnswer);
        /* reset challenge */
        challenge = null;
        return(correctAnswer != null && correctAnswer.equals(answer));
    }

    /**
     * Generate the correct answer.
     * @param password
     * @return encrypted answer
     */
    private String generateAnswer(String password)
    {
        if(method == null)
        {
            return password;
        }
        else if(challenge == null)
        {
            /* return something that will never match any password */
            return getChallenge();
        }
        else
        {
            Logger.debug("auth: using method " + method);
            try
            {
                /*
                 *  TODO: use utf8 (and find a way to convert an utf8 string into
                 *  an array of bytes on the javascript counterpart)
                 */
                Mac mac = Mac.getInstance(method);

                mac.init(new SecretKeySpec(password.getBytes("UTF-8"), method));

                byte[] hash = mac.doFinal(challenge.getBytes("UTF-8"));
                String encoded = new BASE64Encoder().encode(hash);

                /* strips the last(s) '=' */
                int i;

                while((i = encoded.lastIndexOf('=')) != -1)
                {
                    encoded = encoded.substring(0, i);
                }
                return encoded;
            }
            catch(NoSuchAlgorithmException nsae)
            {
                Logger.error("auth: could not find algorithm '" + method + "'");
                Logger.log(nsae);
            }
            catch(Exception e)
            {
                Logger.error("auth: an unknown error occurred...");
                Logger.log(e);
            }
        }
        return null;
    }
}
