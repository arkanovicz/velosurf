package velosurf.web;


import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

@WebListener
public class ContextListener implements ServletContextListener
{
    public void contextInitialized(ServletContextEvent sce)
    {
        currentContext = sce.getServletContext();
    }

    public void contextDestroyed(ServletContextEvent sce)
    {
        currentContext = null;
    }

    public static ServletContext getCurrentContext()
    {
        return currentContext;
    }

    private static ServletContext currentContext = null;
}
