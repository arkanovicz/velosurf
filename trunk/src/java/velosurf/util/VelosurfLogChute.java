package velosurf.util;

import org.apache.velocity.runtime.log.LogChute;

public class VelosurfLogChute implements LogChute {

    void init(RuntimeServices rs) throws Exception {
    }

    void log(int level, String message) {
        Logger.log(level+1,message);
    }

    void log(int level, String message, Throwable t) {
        Logger.log(message,t);
    }

    boolean isLevelEnabled(int level) {
        return Logger.getLogLevel() -1 <= level;
    }

}
