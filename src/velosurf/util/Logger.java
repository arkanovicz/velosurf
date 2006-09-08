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

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.text.SimpleDateFormat;

/** this class is the logger used by velosurf
 */
public class Logger
{
    /**
     * Prefix for trace messages.
     */
    public final static int TRACE_ID = 0;

    /**
     * Prefix for debug messages.
     */
    public final static int DEBUG_ID = 1;

    /** 
     * Prefix for info messages.
     */
    public final static int INFO_ID = 2;
    
    /** 
     * Prefix for warning messages.
     */
    public final static int WARN_ID = 3;

    /** 
     * Prefix for error messages.
     */
    public final static int ERROR_ID = 4;

	/**
	 * Current log level
	 */
	public static int mLogLevel = INFO_ID;

    /**
     * whether the logger captures stdout
     */
    private static boolean sCaptureStdout = false;

    /**
     * whether the logger captures stderr
     */
    private static boolean sCaptureStderr;

    /**
     * max number of lines to log in asynchronous mode
     */
    private static int sAsyncLimit = 50;

    /** Sets the log level
	 * @param inLogLevel log level
	 */
	public static void setLogLevel(int inLogLevel) {
		mLogLevel = inLogLevel;
	}

    /** Gets the current log level
     * @return the current log level
     */
    public static int getLogLevel() {
        return mLogLevel;
    }

	/** date format for timestamps
	 */
	static SimpleDateFormat sFormat = null;
	/** asynchronous log used at start
	 */
	static StringWriter sAsyncLog = null;
	/** log output printwriter
	 */
	static PrintWriter sLog = null;
	static
	{
		try {
			sFormat = new SimpleDateFormat("[yyyy/MM/dd hh:mm:ss]");
			// initialize with an asynchronous buffer
			sAsyncLog = new StringWriter();
			sLog = new PrintWriter(sAsyncLog);
			info("log initialized");
		}
		catch (Throwable e) {
			System.err.println("no log!");
			e.printStackTrace(System.err);
		}
	}
	
	/** stdout old value
	 */
	static PrintStream sOldStdout = null;
	/** stderr old value
	 */
	static PrintStream sOldStderr = null;
		
	/** logs a string
	 * 
	 * @param s string
	 */
	static protected void log(String s) {
		sLog.println(header()+s);
		sLog.flush();
	}
	
	/** logs an exception with a string
	 * 
	 * @param s string
	 * @param e exception
	 */
	static public void log(String s,Throwable e) {
		error(s);
		e.printStackTrace(sLog);
		sLog.flush();
	}
	
	/** log an exception
	 * 
	 * @param e exception
	 */
	static public void log(Throwable e) {
		String msg=e.getMessage();
		log((msg!=null?msg:""),e);
	}

    static int lines = 0;

	/** log a string using a verbose level
	 * 
	 * @param level verbose level
	 * @param s string to log
	 */
	static public void log(int level,String s) {
		if (level < mLogLevel) return;
		String prefix="";
		switch(level) {
			case TRACE_ID:
				prefix=" [trace] ";
				break;
			case DEBUG_ID:
				prefix=" [debug] ";
				break;
			case INFO_ID:
				prefix="  [info] ";
				break;
			case WARN_ID:
				prefix="  [warn] ";
				break;
			case ERROR_ID:
				prefix=" [error] ";
				break;
		}
		log(prefix+s);
        lines++;
        // no more than 100 lines in asynchronous mode
        if (sAsyncLog != null && lines >sAsyncLimit) {
            log2Stderr();
            flushAsyncLog();
            warn("More than "+sAsyncLimit+" lines in asynchronous logging mode...");
            warn("Automatically switching to stderr");
        }
	}
	
	/** logs a tracing string
	 * 
	 * @param s tracing string
	 */
	static public void trace(String s) {
		log(TRACE_ID,s);
	}
	
	/** logs a debug string
	 * 
	 * @param s debug string
	 */
	static public void debug(String s) {
		log(DEBUG_ID,s);
	}
	
	/** logs an info string
	 * 
	 * @param s info string
	 */
	static public void info(String s) {
		log(INFO_ID,s);
	}
	
	/** logs a warning string
	 * 
	 * @param s warning string
	 */
	static public void warn(String s) {
		log(WARN_ID,s);
	}
	
	/** logs an error string
	 * 
	 * @param s error string
	 */
	static public void error(String s) {
		log(ERROR_ID,s);
	}
		
	/** get the output writer
	 * 
	 * @return writer
	 */
	static public PrintWriter getWriter() {
		return sLog;
	}
	
	/** set the output writer
	 * 
	 * @param out PrintWriter or Writer or OutputStream
	 */
	static public void setWriter(Object out) {
		if (sLog!=null) {
			sLog.flush();
			sLog.close();
		}
		if (out instanceof PrintWriter) sLog = (PrintWriter)out;
		else if (out instanceof Writer) sLog = new PrintWriter((Writer)out);
		else if (out instanceof OutputStream) sLog = new PrintWriter((OutputStream)out);
		else throw new RuntimeException("Logger.setWriter: PANIC! class "+out.getClass().getName()+" cannot be used to build a PrintWriter!");
		if (sAsyncLog != null) flushAsyncLog();
	}
	
	/** redirects stdout towards output writer
	 */
	static public void startCaptureStdout() {
		sOldStdout = System.out;
		System.setOut(new PrintStream(new WriterOutputStream(sLog)));
        sCaptureStdout = true;
	}
	
	/** stop redirecting stdout
	 */
	static public void stopCaptureStdout() {
        if (sCaptureStdout) System.setOut(sOldStdout);
        sCaptureStdout = false;
	}

	/** redirects stderr towards the output writer
	 */
	static public void startCaptureStderr() {
		sOldStderr = System.err;
		System.setErr(new PrintStream(new WriterOutputStream(sLog)));
        sCaptureStderr = true;
	}
	
	/** stops redirecting stderr
	 */
	static public void stopCaptureStderr() {
		if (sCaptureStderr) System.setErr(sOldStderr);
        sCaptureStderr = false;
	}

    /** log to stdout
      */
    static public void log2Stdout() {
        stopCaptureStdout();
        stopCaptureStderr();
        sLog = new PrintWriter(System.out);
        flushAsyncLog();
    }

    /** log to stderr
     */
    static public void log2Stderr() {
        stopCaptureStdout();
        stopCaptureStderr();
        sLog = new PrintWriter(System.err);
        flushAsyncLog();
    }

	/** returns "Velosurf "
	 * 
	 * @return return the header
	 */
	static protected String header() {
		return /*sFormat.format(new Date())+*/ " Velosurf ";
	}

	/** flush the asynchronous log in the output writer
	 */
	static protected void flushAsyncLog() {
		if (sAsyncLog != null) {
			try {
				log(sAsyncLog.toString());
				sAsyncLog.close();
				sAsyncLog = null;
			}
			catch (IOException ioe) {
				log(ioe);
			}
		}
	}

	/** dumps the current stack
	 */
    static public void dumpStack() {
        try {
            throw new Exception("dumpStack");
        }
        catch (Exception e) {
            e.printStackTrace(sLog);
        }
    }
	
}
