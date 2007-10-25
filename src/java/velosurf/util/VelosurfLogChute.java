package velosurf.util;

import org.apache.velocity.runtime.RuntimeServices;
import org.apache.velocity.runtime.log.LogChute;

public class VelosurfLogChute implements LogChute {

    public void init(RuntimeServices rs) throws Exception {
    }

    public void log(int level, String message) {
        Logger.log(level+1,message);
    }

    public void log(int level, String message, Throwable t) {
        Logger.log(message,t);
    }

    public boolean isLevelEnabled(int level) {
        return Logger.getLogLevel() -1 <= level;
    }

}
