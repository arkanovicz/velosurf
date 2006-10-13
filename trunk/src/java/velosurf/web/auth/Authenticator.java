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
import java.util.Random;
import java.math.BigInteger;

import org.apache.velocity.tools.view.context.ViewContext;

import velosurf.util.Logger;

/**
 * This abstract class implements an authentication mechanism. It is meant to be declared
 * in toolbox.xml as a session-scoped tool.
 *
 * You will need to implement the same password encryption on the client side using the provided
 * javascript files and the example login.html.vtl page.
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */

public abstract class Authenticator {

    protected abstract String getPassword(String login);
    protected abstract Object getUser(String login);

    public void init(Object initData) {
        if (initData instanceof ViewContext) {
            _session = ((ViewContext)initData).getRequest().getSession();
        } else {
            Logger.error("Authentifier tool should be used in a session scope!");
        }
    }

    public BigInteger getChallenge() {
        BigInteger challenge = new BigInteger(1024,_random);
        _session.setAttribute("challenge",challenge);
        return challenge;
    }

    public boolean checkLogin(String login,String answer) {
        String password = getPassword(login);
        /* TODO we may want an option to accept empty passwords */
        if(password == null || password.length() == 0) {
            return false;
        }
        String correctAnswer = generateAnswer((BigInteger)_session.getAttribute("challenge"),hash(password)).toString();
        Logger.trace("auth: received="+answer);
        Logger.trace("auth: correct ="+correctAnswer);
        return (correctAnswer.equals(answer));
    }

    // explicit hash calculation to remain the same with java & javascript versions
    private long hash(String password) {
        long hash = 0;
        int len = password.length();
        for(int i=0; i<len; i++)
            hash += password.charAt(i) * (long)Math.pow(31,len-(i+1));
        return hash;
    }

    private BigInteger generateAnswer(BigInteger challenge, long passwordHash) {
        return challenge.modPow(new BigInteger(""+passwordHash),_strongPrime);
    }

    public BigInteger getPrime() {
        return _strongPrime;
    }

    private static Random _random = new Random(System.currentTimeMillis());
    private static BigInteger _strongPrime = new BigInteger("105247318603788819436722815711410650558788661449526175610519780753696201588701876008528752401364127206884634662808952196656409364008838765730690399648197844554531010991641365205678334474881184684846779693500798778111948351206589133108027732009643706433078079273746999639315426538479260254617064513576352532751");

    protected HttpSession _session = null;

}
