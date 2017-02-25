package velosurf.web;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.io.Writer;
import java.io.UnsupportedEncodingException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.velocity.context.Context;
import org.apache.velocity.exception.MethodInvocationException;
import org.apache.velocity.exception.ParseErrorException;
import org.apache.velocity.exception.ResourceNotFoundException;
import org.apache.velocity.io.VelocityWriter;
import org.apache.velocity.Template;
import org.apache.velocity.context.Context;
import org.apache.velocity.tools.view.VelocityViewServlet;

import velosurf.util.Logger;

public class ActionServlet extends VelocityViewServlet {

    /**
     *  Handles with both GET and POST requests
     *
     *  @param request  HttpServletRequest object containing client request
     *  @param response HttpServletResponse object for the response
     */
    protected void doRequest(HttpServletRequest request,
                             HttpServletResponse response)
    {
        Context context = null;
        try
        {
            // first, get a context
            context = createContext(request, response);

            // set the content type
            setContentType(request, response);

            // get the template
            Template template = handleRequest(request, response, context);

            // bail if we can't find the template
            if (template == null)
            {
                Logger.warn("VelocityViewServlet: couldn't find template to match request.");
                return;
            }

            // debug behaviour
            if(request.getParameter("debug") != null)
            {
              context.put("debug",1);
            }

            // merge the template and context
            mergeTemplate(template, context, response);
        }
        catch (Exception e)
        {
			Logger.error("ActionServlet: Exception processing the template: "+e);

    	    // call the error handler to let the derived class
		    // do something useful with this failure.
		    error(request, response, e);
        }
        finally
        {
            // call cleanup routine to let a derived class do some cleanup
            requestCleanup(request, response, context);
        }
    }


    protected void mergeTemplate(Template template,
                                 Context context,
                                 HttpServletResponse response)
        throws ResourceNotFoundException, ParseErrorException,
               MethodInvocationException, IOException,
               UnsupportedEncodingException
    {
        VelocityWriter vw = null;
        Writer writer = new StringWriter();
        try
        {
            getVelocityView().merge(template, context, writer);
        }
        finally
        {
            writer.flush();
            /* debug behaviour */
            if(context.get("debug") != null)
            {
                PrintWriter respWriter = response.getWriter();
                respWriter.print(writer.toString());
                respWriter.flush();
            }
        }
    }
}
