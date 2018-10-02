package velosurf.util;

import org.apache.velocity.VelocityContext;
import org.apache.velocity.tools.generic.RenderTool;

import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DynamicQueryBuilder
{
  // Not needed
  // private static Pattern balancedQuotes  = Pattern.compile("'[^']*?'|\"[^\"]*?\"");

  static protected RenderTool renderTool = new RenderTool();

  public static boolean isDynamic(String query)
  {
    // Not needed
    /*
    StringBuilder unquoted = new StringBuilder();
    Matcher matcher = balancedQuotes.matcher(query);
    int pos = 0;
    while (matcher.find())
    {
      if (matcher.start() < pos) continue;
      unquoted.append(query.substring(pos, matcher.start()));
      pos = matcher.end();
    }
    unquoted.append(query.substring(pos));
    String str = unquoted.toString();
    return str.indexOf('#') != -1 || str.indexOf('$') != -1;
    */
    return query.indexOf('#') != -1 || query.indexOf('$') != -1;
  }

  public static String buildQuery(String vtl, SlotMap source)
  {
    String query = null;
    try
    {
      query = renderTool.eval(new VelocityContext(Collections.<String, Object>unmodifiableMap(source)), vtl);
    }
    catch (Exception e)
    {
      Logger.error("could not evaluate expression " + vtl + e.getMessage());
    }
    return query;
  }
}
