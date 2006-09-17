import javax.servlet.ServletContext;

import org.apache.velocity.tools.view.context.ViewContext;

import velosurf.model.Entity;
import velosurf.sql.Database;
import velosurf.util.Logger;

public class DBLocalizer extends HTTPLocalizerTool
{
	public DBLocalizer() {}

    public void init(Object initData) {
		super.init(initData);
		if (!initialized) {
	        ServletContext ctx = 
	          inViewContext instanceof ServletContext ?
    	        ctx = (ServletContext)inViewContext   :
        	    ctx = ((ViewContext)inViewContext).getServletContext();
			readLocales(ctx);
		}
	}

	private static synchronized readLocales(ServletContext ctx) {
		if (_initialized) return;

		try {
			Database db = VelosurfTool.getDefaultInstance(ctx);
			if (db==null) throw new Exception("Cannot find database!");
			Entity entity = db.getEntity("localized");
			if (entity==null) throw new Exception("Cannot find 'localized' database entity!");
			_localeStrings = new Map<<Locale,Map<Object,String>>;
			RowIterator locales = entity.query();
			while (locales.hasNext()) {
				Instance row = (Instance)locales.next();
				String locale = (String)row.get("locale");
				String string = (String)row.get("string");
				
			}

			_initialized = true;
		} catch (Exception e) {
			Logger.log(e);
		}
	}

	public String get(Object id) {
		return "";
	}

	private static boolean _initialized = false;

	protected static Map<Locale,Map<Object,String>> _localeStrings = null;
	protected static Map<Object,String> _
}


