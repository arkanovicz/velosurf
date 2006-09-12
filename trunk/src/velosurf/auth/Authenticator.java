package velosurf.auth;

import org.apache.velocity.tools.view.context.ViewContext;

import javax.servlet.http.HttpSession;
import java.util.Random;
import java.math.BigInteger;

import util.Logger;

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
        String correctAnswer = generateAnswer((BigInteger)_session.getAttribute("challenge"),hash(password)).toString();
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
