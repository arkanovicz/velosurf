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

package velosurf.sql;

import java.util.Iterator;
import java.util.List;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Arrays;
import java.util.Date;
import java.util.regex.Pattern;
import java.io.InputStream;
import java.sql.SQLException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;

import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Text;
import org.jdom.input.SAXBuilder;

import velosurf.util.Logger;
import velosurf.util.StringLists;
import velosurf.util.Strings;
import velosurf.util.XIncludeResolver;
import velosurf.cache.Cache;
import velosurf.model.Entity;
import velosurf.model.Action;
import velosurf.model.Attribute;
import velosurf.model.Transaction;
import velosurf.model.ImportedKey;
import velosurf.model.ExportedKey;
import velosurf.validation.Email;
import velosurf.validation.Length;
import velosurf.validation.Range;
import velosurf.validation.NotNull;
import velosurf.validation.OneOf;
import velosurf.validation.Reference;
import velosurf.validation.Regex;
import velosurf.validation.FieldConstraint;
import velosurf.validation.DateRange;
import velosurf.validation.NotEmpty;

/** A configuration loader for the Database object.
 *
 *  @author <a href="mailto:claude.brisson@gmail.com">Claude Brisson</a>
 */

public class ConfigLoader {

    /** Database. */
    private Database database = null;
    /** &lt;<code>xi:include</code>&gt; tag resolver. */
    private XIncludeResolver xincludeResolver = null;
    /** Syntax checker pattern for the <code>result</code> attribute of &lt;<code>attribute</code>&gt; tags. */
    private static final Pattern attributeResultSyntax = Pattern.compile("^scalar|(?:(?:row|rowset)(?:/.+)?)$");
    /**
     * Constructor.
     * @param db database
     */
    public ConfigLoader(Database db) {
        this(db,null);
    }
    /**
     * Constructor.
     * @param db database
     * @param xincludeResolver &lt;<code>xi:include</code>&gt; tag resolver
     */
    public ConfigLoader(Database db,XIncludeResolver xincludeResolver) {
        database = db;
        this.xincludeResolver = xincludeResolver;
    }

   /**
    * Main method of the ConfigLoader.
    * @param config the configuration input stream
    * @throws Exception
    */
    public void loadConfig(InputStream config) throws Exception {

        Logger.info("reading properties...");

        /* build JDOM tree */
        Document document = new SAXBuilder().build(config);
        if (xincludeResolver != null) {
            document = xincludeResolver.resolve(document);
        }
        Element database = document.getRootElement();

        setDatabaseAttributes(database);

        /* define root attributes */
        defineAttributes(database,this.database.getRootEntity());

        /* define root actions */
        defineActions(database,this.database.getRootEntity());


        /* define entities */
        defineEntities(database);

        Logger.info("Config file successfully read.");
    }

    /**
     * Adapt the case to match chosen database case policy.
     * @param str string to adapt
     * @return adapted string
     */
    private String adaptCase(String str) {
        return database.adaptCase(str);
    }


    /**
     * Parses database XML attributes.
     * @param database parent element
     */
    private void setDatabaseAttributes(Element database)  {

        /* log level */
        String loglevel = database.getAttributeValue("loglevel");
        if (checkSyntax("loglevel",loglevel,new String[]{"trace","debug","info","warn","error","fatal"})) {
            if ("trace".equalsIgnoreCase(loglevel)) Logger.setLogLevel(Logger.TRACE_ID);
            else if ("debug".equalsIgnoreCase(loglevel)) Logger.setLogLevel(Logger.DEBUG_ID);
            else if ("info".equalsIgnoreCase(loglevel)) Logger.setLogLevel(Logger.INFO_ID);
            else if ("warn".equalsIgnoreCase(loglevel)) Logger.setLogLevel(Logger.WARN_ID);
            else if ("error".equalsIgnoreCase(loglevel)) Logger.setLogLevel(Logger.ERROR_ID);
            else if ("fatal".equalsIgnoreCase(loglevel)) Logger.setLogLevel(Logger.FATAL_ID);
            else Logger.error("Invalid loglevel. Should be one of: trace, debug, info, warn, error, fatal.");
        }

        /* default-access - deprecated - for compatibility only, replaced with read-only=true|false */
        String access = database.getAttributeValue("default-access");
        if (access != null) {
            Logger.warn("The syntax <database default-access=\"rw|ro\"> is deprecated.");
            Logger.warn("Please use <database read-only=\"true|false\"> instead.");
            if (checkSyntax("access",access,new String[]{"ro","rw"})) {
                this.database.setReadOnly(!access.equalsIgnoreCase("rw"));
            }
        }

        /* read-only */
        String ro = database.getAttributeValue("read-only");
        if (ro != null) {
            /* check syntax but continue anyway with read-only database if the syntax is bad */
            checkSyntax("read-only",ro,new String[] {"true","false"});
            /* default to true - using Boolean.parseBoolean is not possible */
            this.database.setReadOnly(!ro.equalsIgnoreCase("false"));
        }

        String caching = database.getAttributeValue("default-caching");
        if(caching != null) {
            Logger.warn("attribute 'default-caching' is deprecatd, please use 'caching' instead.");
        } else {
            caching = database.getAttributeValue("caching");
        }
        if (checkSyntax("caching",caching,new String[] {"none","no","yes","soft","full"})) {
            int val = parseCaching(caching);
            this.database.setCaching(val);
            if (val == Cache.FULL_CACHE) Logger.warn("The 'full' caching method is deprecated and will be removed in future versions.");
        }

        String reverseMode = database.getAttributeValue("reverse");
        if (checkSyntax("reverse",reverseMode,new String[]{"none","partial","tables","full"})) {
            int mode = -1;
            if ("full".equalsIgnoreCase(reverseMode) || reverseMode == null) {
                mode = ReverseEngineer.REVERSE_FULL;
            }
            else if ("partial".equalsIgnoreCase(reverseMode)) {
                mode = ReverseEngineer.REVERSE_PARTIAL;
            }
            else if ("tables".equalsIgnoreCase(reverseMode)) {
                mode = ReverseEngineer.REVERSE_TABLES;
            }
            else if ("none".equalsIgnoreCase(reverseMode)) {
                mode = ReverseEngineer.REVERSE_NONE;
            }
            this.database.getReverseEngineer().setReverseMode(mode);
        }

        Element credentials = database.getChild("credentials");
        if (credentials == null) {
            credentials = database;
        }

        this.database.setUser(credentials.getAttributeValue("user"));
        this.database.setPassword(credentials.getAttributeValue("password"));

		String url = credentials.getAttributeValue("url");
        if (url == null) {
            url = database.getAttributeValue("url");
        }
        this.database.setURL(url);

        String driver = credentials.getAttributeValue("driver");
        if (driver == null) {
            driver = database.getAttributeValue("driver");
        }
        this.database.setDriver(driver);

        String schema = adaptCase(credentials.getAttributeValue("schema"));
        if (schema == null) {
            schema = adaptCase(database.getAttributeValue("schema"));
        }
        if (schema != null) {
            this.database.setSchema(schema);
        }

        /* load driver now so as to know default behaviours */
        this.database.loadDriver();

        int min = 0;
        String minstr = database.getAttributeValue("min-connections");
        if (minstr != null) {
            try {
                min = Integer.parseInt(minstr);
                if (min>0) this.database.setMinConnections(min);
                else Logger.error("the parameter 'min-connections' wants an integer > 0 !");
            }
            catch(NumberFormatException nfe) {
                Logger.error("the parameter 'min-connections' wants an integer!");
            }
        }

        String maxstr = database.getAttributeValue("max-connections");
        if (maxstr != null) {
            try {
                int max = Integer.parseInt(maxstr);
                if (max>=min) this.database.setMaxConnections(max);
                else Logger.error("the parameter 'max-connections' must be >= min-connection!");
            }
            catch(NumberFormatException nfe) {
                Logger.error("the parameter 'max-connections' wants an integer!");
            }
        }

        this.database.setSeed(database.getAttributeValue("seed"));

        String caseSensivity = database.getAttributeValue("case");
        /* if case-sensivity has not been set explicitely, deduce it from the driver */
        if (caseSensivity == null) {
            caseSensivity = this.database.getDriverInfo().getCaseSensivity();
        }
        if (checkSyntax("case",caseSensivity,new String[]{"sensitive","uppercase","lowercase"})) {
            Logger.info("Case sensivity: "+caseSensivity);
            if("sensitive".equalsIgnoreCase(caseSensivity)) {
                this.database.setCase(Database.CASE_SENSITIVE);
            } else if("uppercase".equalsIgnoreCase(caseSensivity)) {
                this.database.setCase(Database.UPPERCASE);
            } else if ("lowercase".equalsIgnoreCase(caseSensivity)) {
                this.database.setCase(Database.LOWERCASE);
            }
        }

        /* root database entity (never read-only koz not a real entity and we must allow external parameters */
        Entity root = new Entity(this.database,"velosurf.root",false,Cache.NO_CACHE);
        this.database.addEntity(root);
    }

    /**
     * Define Velosurf attributes.
     * @param parent parent XML element
     * @param entity parent entity
     * @throws SQLException
     */
    @SuppressWarnings("deprecation") private void defineAttributes(Element parent,Entity entity) throws SQLException {
        for (Iterator attributes = parent.getChildren("attribute").iterator();attributes.hasNext();) {
            Element element = (Element)attributes.next();
            String name = adaptCase(element.getAttributeValue("name"));
            Attribute attribute = new Attribute(name,entity);
            String result = element.getAttributeValue("result");
            if (result == null) {
                Logger.warn("Attribute '"+name+"' doesn't have a 'result' attribute... using rowset as default.");
                result = "rowset";
            }
            if(attributeResultSyntax.matcher(result).matches()) {
                int type = 0;
                if (result.equals("scalar")) {
                    type = Attribute.SCALAR;
                } else if (result.startsWith("rowset")) {
                    type = Attribute.ROWSET;
                } else if (result.startsWith("row")) {
                    type = Attribute.ROW;
                } else {
                    throw new SQLException("bad syntax for the 'result' attribute: "+result);
                }
                attribute.setResultType(type);
                int slash = result.indexOf("/");
                if (slash>-1 && slash+1<result.length()) {
                    attribute.setResultEntity(adaptCase(result.substring(slash+1)));
                }
            }

            String foreignKey = element.getAttributeValue("foreign-key");
            if(foreignKey != null) {
                Logger.warn("use of the foreign-key attribute for the <attribute> tag is deprecated. Please use <import-key>");
                if(attribute.getResultEntity() == null) {
                    throw new SQLException("Attribute '"+name+"' is a foreign key, Velosurf needs to know its result entity!");
                }
                attribute.setForeignKeyColumn(foreignKey);
            }

            /* attribute parameters and query */
            if (foreignKey != null) {
                attribute.addParamName(adaptCase(foreignKey));
            } else {
                String query = "";
                Iterator queryElements = element.getContent().iterator();
                while (queryElements.hasNext()) {
                    Object content = queryElements.next();
                    if (content instanceof Text) query += Strings.trimSpacesAndEOL(((Text)content).getText());
                    else if (content instanceof Element) {
                        query+=" ? ";
                        Element elem = (Element)content;
                        attribute.addParamName(adaptCase(elem.getName()));
                    }
                    else{
                        Logger.error("Try upgrading your jdom library!");
                        throw new SQLException("Was expecting an org.jdom.Element, found a "+content.getClass().getName()+": '"+content+"'");
                    }
                }
                /* trim */
                query = Pattern.compile(";\\s*\\Z").matcher(query).replaceFirst("");
                attribute.setQuery(query);
            }

            /* caching */
            String caching = element.getAttributeValue("caching");
            if (checkSyntax("caching",caching,new String[] {"no","yes"})) {
                attribute.setCaching(caching.equals("yes"));
			}

            entity.addAttribute(attribute);
        }
    }

    /**
     * Define foreign keys.
     * @param parent parent XML element
     * @param entity parent entity
     */
    private void defineForeignKeys(Element parent,Entity entity) {
        for (Iterator imported = parent.getChildren("imported-key").iterator();imported.hasNext();) {
            Element keyelem = (Element)imported.next();
            String name = keyelem.getAttributeValue("name");
            if (name == null) {
                Logger.error("tag <imported-keyelem> needs a 'name' attribute!");
                continue;
            }
            String pkEntity = keyelem.getAttributeValue("entity");
            if (pkEntity == null) {
                Logger.error("tag <imported-keyelem> needs an 'entity' attribute (name='"+name+"')!");
                continue;
            }
            List<String> fkCols = null;
            String foreignCols = keyelem.getAttributeValue("foreign-cols");
            if (foreignCols != null) {
                fkCols = new ArrayList<String>();
                List<String> aliases = Arrays.asList(foreignCols.split(","));
                
                /* resolve names */
                for(String col:aliases) {
                    fkCols.add(entity.resolveName(col));
                }
            }

			ImportedKey importedKey = new ImportedKey(name,entity,pkEntity,fkCols);

            /* caching */
            String caching = keyelem.getAttributeValue("caching");
            if (checkSyntax("caching",caching,new String[] {"no","yes"})) {
                importedKey.setCaching(caching.equals("yes"));
			}

            entity.addAttribute(importedKey);
        }
        for (Iterator exported = parent.getChildren("exported-key").iterator();exported.hasNext();) {
            Element keyelem = (Element)exported.next();
            String name = keyelem.getAttributeValue("name");
            if (name == null) {
                Logger.error("tag <exported-keyelem> needs a 'name' attribute!");
                continue;
            }
            String pkEntity = keyelem.getAttributeValue("entity");
            if (pkEntity == null) {
                Logger.error("tag <exported-keyelem> needs an 'entity' attribute (name='"+name+"')!");
                continue;
            }
            List<String> fkCols = null;
            String foreignCols = keyelem.getAttributeValue("foreign-cols");
            if (foreignCols != null) {
                fkCols = new ArrayList<String>();
                List<String> aliases = Arrays.asList(foreignCols.split(","));

                /* resolve names */
                for(String col:aliases) {
                    fkCols.add(entity.resolveName(col));
                }
            }

            ExportedKey exportedKey = new ExportedKey(name,entity,pkEntity,fkCols);

            /* caching */
            String caching = keyelem.getAttributeValue("caching");
            if (checkSyntax("caching",caching,new String[] {"no","yes"})) {
                exportedKey.setCaching(caching.equals("yes"));
			}

            entity.addAttribute(exportedKey);
        }
    }

    /**
     * Define actions.
     * @param parent parent XML element
     * @param entity parent entity
     */
    private void defineActions(Element parent,Entity entity) {
        for (Iterator actions = parent.getChildren("action").iterator();actions.hasNext();) {
            Element element = (Element)actions.next();
            String name = adaptCase(element.getAttributeValue("name"));
            Action action = null;
            if(isTransaction(element)) {
                Transaction transaction = new Transaction(name,entity);
                action = transaction;
                List<String> queries = new ArrayList<String>();
                List<List<String>> parameters = new ArrayList<List<String>>();
                StringBuilder query = new StringBuilder();
                List<String> paramNames = new ArrayList<String>();
                Iterator queryElements = element.getContent().iterator();
                while (queryElements.hasNext()) {
                    Object content = queryElements.next();
                    if (content instanceof Text) {
                        String text = Strings.trimSpacesAndEOL(((Text)content).getText());
                        int i = text.indexOf(';');
                        if (i!=-1) {
                            query.append(text.substring(0,i));
                            queries.add(query.toString());
                            parameters.add(paramNames);
                            query = new StringBuilder();
                            paramNames = new ArrayList<String>();
                        }
                        query.append(text.substring(i+1));
                    }
                    else {
                        query.append(" ? ");
                        Element elem = (Element)content;
                        paramNames.add(elem.getName());
                    }
                }
                if (query.length()>0) {
                    queries.add(query.toString());
                    parameters.add(paramNames);
                }
                transaction.setQueries(queries);
                transaction.setParamNamesLists(parameters);
            } else { /* simple action */
                action = new Action(name,entity);
                String query = "";
                Iterator queryElements = element.getContent().iterator();
                while (queryElements.hasNext()) {
                    Object content = queryElements.next();
                    if (content instanceof Text) {
                        query += Strings.trimSpacesAndEOL(((Text)content).getText());
                    }
                    else {
                        query += " ? ";
                        Element elem = (Element)content;
                        action.addParamName(adaptCase(elem.getName()));
                    }
                }
                action.setQuery(query);
            }
            entity.addAction(action);
        }
    }

    /**
     * Define entities.
     * @param database database XML element
     * @throws Exception
     */
    private void defineEntities(Element database) throws Exception {
        for (Iterator entities = database.getChildren("entity").iterator();entities.hasNext();) {
            Element element = (Element)entities.next();
            String origName = element.getAttributeValue("name");
            element.removeAttribute("name");
            String name = adaptCase(origName);
            String table = adaptCase(element.getAttributeValue("table"));
            element.removeAttribute("table");

            Entity entity = this.database.getEntityCreate(adaptCase(name));
            if (table != null) {
                entity.setTableName(table);
            }
            this.database.getReverseEngineer().addTableMatching(entity.getTableName(),entity);

            /* custom class */
            String cls = element.getAttributeValue("class");
            element.removeAttribute("class");
            if (cls != null) {
                try {
                    Class clazz = Class.forName(cls);
                    clazz.newInstance();
                    /* looks ok */
                    entity.setInstanceClass(cls);
                } catch(Throwable t) {
                    Logger.error("Cannot instantiate class "+cls);
                    Logger.log(t);
                }
            }


            /* access (deprecated) */
            String access = element.getAttributeValue("access");
            if (access != null) {
                Logger.warn("The syntax <entity access=\"rw|ro\"> is deprecated.");
                Logger.warn("Please use <entity read-only=\"true|false\"> instead.");
                if (checkSyntax(name+".access",access,new String[]{"ro","rw"})) {
                    access = access.toLowerCase();
                    if (access.equalsIgnoreCase("ro")) entity.setReadOnly(true);
                    else if (access.equalsIgnoreCase("rw")) entity.setReadOnly(false);
                }
            }

            /* read-only */
            String ro = element.getAttributeValue("read-only");
            element.removeAttribute("read-only");
            if (ro != null) {
                /* check syntax but continue anyway with read-only database if the syntax is bad */
                checkSyntax("read-only",ro,new String[] {"true","false"});
                /* default to true - using Boolean.parseBoolean is not possible */
                this.database.setReadOnly(!ro.equalsIgnoreCase("false"));
            }

            /* caching */
            String caching = element.getAttributeValue("caching");
            element.removeAttribute("caching");
            if (checkSyntax("caching",caching,new String[] {"none","no","yes","soft","full"}))
                entity.setCachingMethod(parseCaching(caching));

            /* obfuscation */
            String obfuscate = element.getAttributeValue("obfuscate");
            element.removeAttribute("obfuscate");
            if (obfuscate != null) {
                List<String> obfuscatedCols = new ArrayList<String>();
                StringTokenizer tokenizer = new StringTokenizer(obfuscate,", ");
                while(tokenizer.hasMoreTokens()) {
                    obfuscatedCols.add(adaptCase(tokenizer.nextToken()));
                }
                entity.setObfuscated(obfuscatedCols);
            }

            /* localization */
            String localize = element.getAttributeValue("localize");
            element.removeAttribute("localize");
            if (localize != null) {
                List<String> localizedCols = new ArrayList<String>();
                StringTokenizer tokenizer = new StringTokenizer(localize,", ");
                while(tokenizer.hasMoreTokens()) {
                    localizedCols.add(adaptCase(tokenizer.nextToken()));
                }
                entity.setLocalized(localizedCols);
            }

            /* aliases */
            Element aliases = element.getChild("aliases");
            if (aliases != null) {
                for(org.jdom.Attribute att:(List<org.jdom.Attribute>)aliases.getAttributes()) {
                    String column = att.getValue();
                    String alias = att.getName();
                    entity.addAlias(alias,column);
                }
            }

            /* define entity attributes */
            defineAttributes(element,entity);

            /* define entity actions */
            defineActions(element,entity);

            /* define entity imported and exported keys */
            defineForeignKeys(element,entity);

            /* define entity constraints */
            defineConstraints(element,entity);
        }
    }

    /**
     * Define constraints.
     * @param element parent XML element
     * @param entity parent entity
     * @throws Exception
     */
    private void defineConstraints(Element element,Entity entity) throws Exception {
        DateFormat format = new SimpleDateFormat("yyyyMMdd");
        String str;
        for (Iterator columns = element.getChildren("constraint").iterator();columns.hasNext();) {
            Element colElement = (Element)columns.next();
            String column = colElement.getAttributeValue("column");
            if(column == null) {
                Logger.error("constraint tag needs a 'column' attribute");
                continue;
            }
            colElement.removeAttribute("column");

            String type;
            boolean hasType = ( (type = colElement.getAttributeValue("type")) != null);

            /* short-syntax length */
            int minLen = 0,maxLen = Integer.MAX_VALUE;
            String minstr = colElement.getAttributeValue("min-len");
            String maxstr = colElement.getAttributeValue("max-len");
            if (minstr != null || maxstr != null) {
                colElement.removeAttribute("min-len");
                colElement.removeAttribute("max-len");
                if (minstr != null) {
                    minLen = Integer.parseInt(minstr);
                }
                if (maxstr != null) {
                    maxLen = Integer.parseInt(maxstr);
                }
                entity.addConstraint(column,new Length(minLen,maxLen));
            }
            /* short-syntax range */
            minstr = colElement.getAttributeValue("min");
            maxstr = colElement.getAttributeValue("max");
            if (minstr != null || maxstr != null || hasType && ("integer".equals(type) | "number".equals(type))) {
                colElement.removeAttribute("min");
                colElement.removeAttribute("max");
                Range numberConstraint = null;
                numberConstraint = new Range();
                if (minstr != null) {
                    Number min = Double.parseDouble(minstr);
                    numberConstraint.setMin(min);
                }
                if (maxstr != null) {
                    Number max = Double.parseDouble(maxstr);
                    numberConstraint.setMax(max);
                }
                if (hasType && "integer".equals(type)) {
                    numberConstraint.setInteger(true);
                    colElement.removeAttribute("type");
                }
                entity.addConstraint(column,numberConstraint);
            }
            /* short-syntax date range - use yyyyMMdd date format */
            String afterstr = colElement.getAttributeValue("after");
            String beforestr = colElement.getAttributeValue("before");
            if (afterstr != null || beforestr != null || hasType && "date".equals(type)) {
                colElement.removeAttribute("after");
                colElement.removeAttribute("before");
                DateRange dateConstraint  = new DateRange();
                if(afterstr != null) {
                    Date after = format.parse(afterstr);
                    dateConstraint.setAfterDate(after);
                }
                if(beforestr != null) {
                    Date before = format.parse(beforestr);
                    dateConstraint.setBeforeDate(before);
                }
                if(hasType && "date".equals(type)) {
                    colElement.removeAttribute("type");
                }
                entity.addConstraint(column,dateConstraint);
            }
            /* short-syntax, email */
            if(hasType && "email".equals(type)) {
                entity.addConstraint(column,new Email());
                colElement.removeAttribute("type");
            }
            /* short-syntax, others */
            for(Iterator atts = colElement.getAttributes().iterator();atts.hasNext();) {
                org.jdom.Attribute attribute = (org.jdom.Attribute)atts.next();
                String name = attribute.getName();
                String value = attribute.getValue();
                if (name.equals("not-null")) {
                    if(attribute.getBooleanValue()) {
                        entity.addConstraint(column, new NotNull());
                    }
                } if (name.equals("not-empty")) {
                    if(attribute.getBooleanValue()) {
                        entity.addConstraint(column, new NotEmpty());
                    }
                } else if (name.equals("one-of")) {
                    entity.addConstraint(column,new OneOf(Arrays.asList(value.split(","))));
                } else if (name.equals("reference")) {
                    int dot = value.indexOf(".");
                    if (dot == -1 || dot == value.length()-1) {
                        Logger.error("bad syntax for reference constraint (entity "+entity.getName()+", column "+column+"). Should be 'table.column'.");
                    } else {
                        String table = value.substring(0,dot);
                        String col = value.substring(dot+1);
                        entity.addConstraint(column,new Reference(database,table,col));
                    }
                } else if (name.equals("regex")) {
                    entity.addConstraint(column,new Regex(Pattern.compile(value)));
                } else {
                    if (!name.equals("name")) {
                        Logger.error("ignoring unknown constraint '"+name+"="+attribute.getValue()+"' (entity "+entity.getName()+", column "+column+").");
                    }
                }
            }
            /* long syntax */
            Length length = null;
            for (Iterator constraints = colElement.getChildren().iterator();constraints.hasNext();) {
                Element constraintElement = (Element)constraints.next();
                String name = constraintElement.getName();
                FieldConstraint constraint = null;
                if (name.equals("email")) {
                    boolean dnsCheck = false;
                    boolean smtpCheck = false;
                    str = constraintElement.getAttributeValue("dns-check");
                    if (checkSyntax("dns-check",str,new String[]{"yes","no"})) {
                        dnsCheck = (str.equalsIgnoreCase("yes"));
                    }
                    str = constraintElement.getAttributeValue("smtp-check");
                    if (checkSyntax("smtp-check",str,new String[]{"yes","no"})) {
                        smtpCheck = (str.equalsIgnoreCase("yes"));
                    }
                    constraint = new Email(dnsCheck,smtpCheck);
                } else if (name.equals("min-len")) {
                    if (length != null) {
                        length.setMinLength(Integer.parseInt(constraintElement.getAttributeValue("value")));
                    } else {
                        constraint = length = new Length(Integer.parseInt(constraintElement.getAttributeValue("value")),Integer.MAX_VALUE);
                    }
                } else if (name.equals("max-len")) {
                    if (length != null) {
                        length.setMaxLength(Integer.parseInt(constraintElement.getAttributeValue("value")));
                    } else {
                        constraint = length = new Length(0,Integer.parseInt(constraintElement.getAttributeValue("value")));
                    }
                } else if (name.equals("integer") || name.equals("number")) {
                    Range range = new Range();
                    range.setInteger(name.equals("integer"));
                    minstr = constraintElement.getAttributeValue("min");
                    if (minstr != null) {
                        range.setMin(Double.parseDouble(minstr));
                    }
                    maxstr = constraintElement.getAttributeValue("max");
                    if (maxstr != null) {
                        range.setMax(Double.parseDouble(maxstr));
                    }
                    constraint = range;
                } else if (name.equals("date")) {
                    DateRange daterange = new DateRange();
                    minstr = constraintElement.getAttributeValue("after");
                    if (minstr != null) {
                        daterange.setAfterDate(format.parse(minstr));
                    }
                    maxstr = constraintElement.getAttributeValue("before");
                    if(maxstr != null) {
                        daterange.setBeforeDate(format.parse(maxstr));
                    }
                    String dateformat = constraintElement.getAttributeValue("format");
                    if (dateformat != null) {
                        daterange.setDateFormat(new SimpleDateFormat(dateformat));
                    }
                    constraint = daterange;
                } else if (name.equals("not-null")) {
                    constraint = new NotNull();
                }  else if (name.equals("not-empty")) {
                    constraint = new NotEmpty();
                } else if (name.equals("one-of")) {
                    List<String> values = new ArrayList<String>();
                    for(Iterator it = constraintElement.getChildren("value").iterator();it.hasNext();) {
                        values.add((String)((Element)it.next()).getText());
                    }
                    constraint = new OneOf(values);
                } else if (name.equals("reference")) {
                    String fk = constraintElement.getAttributeValue("foreign-key");
                    if(fk == null) {
                        Logger.error("reference constraint needs a 'foreign-key' attribute");
                    }
                    int dot = fk.indexOf(".");
                    if (dot == -1 || dot == fk.length()-1) {
                        Logger.error("bad syntax for reference constraint (entity "+entity.getName()+", column "+column+"). Should be 'table.column'.");
                    } else {
                        String table = fk.substring(0,dot);
                        String col = fk.substring(dot+1);
                        constraint = new Reference(database,table,col);
                    }
                } else if (name.equals("regex")) {
                    constraint = new Regex(Pattern.compile(constraintElement.getAttributeValue("pattern")));
                } else {
                    Logger.error("ignoring unknown constraint '"+name+"' (entity \"+entity.getName()+\", column \"+column+\").");
                }
                if (constraint != null) {
                    String msg = constraintElement.getAttributeValue("message");
                    if (msg != null) {
                        constraint.setMessage(msg);
                    }
                    entity.addConstraint(column,constraint);
                }
            }
        }
    }

    /** Check the syntax of a parameter in the config file.
     *
     * @param paramName name of the parameter
     * @param paramValue value of the parameter
     * @param possibleValues possible values for the parameter
     * @return whether the syntax is correct
     */
    private boolean checkSyntax(String paramName, String paramValue, String[] possibleValues) {
        if (paramValue == null) return false;
        List possible = Arrays.asList(possibleValues);
        if (paramValue!=null && Arrays.asList(possibleValues).contains(paramValue.toLowerCase()))
            return true;
        else {
            Logger.error("Parameter '"+paramName+"' wants one of: " + StringLists.join(possible,","));
            return false;
        }
    }

    /** Parse a caching value.
     *
     * @param caching string describing the type of caching
     * @return type of caching
     */
    private static int parseCaching(String caching) {
        return
            caching == null || caching.equalsIgnoreCase("none") || caching.equalsIgnoreCase("no") ? Cache.NO_CACHE :
            caching.equalsIgnoreCase("soft") || caching.equalsIgnoreCase("yes") ? Cache.SOFT_CACHE :
            caching.equalsIgnoreCase("full") ? Cache.FULL_CACHE :
            Cache.NO_CACHE;
    }

    /** Check whether the action defined by this XML tree is a simple action or a transaction.
     *
     * @param element XML tree defining an action
     * @return true if the action is a transaction
     */
    public static boolean isTransaction(Element element) {
        Iterator queryElements = element.getContent().iterator();
        while (queryElements.hasNext()) {
            Object content = queryElements.next();
            if (content instanceof Text) {
                String text = Strings.trimSpacesAndEOL(((Text)content).getText());
                char[] chars = text.toCharArray();
                boolean insideLitteral = false;
                for (int i=0;i<chars.length;i++) {
                    if(chars[i] == '\'') insideLitteral = !insideLitteral;
                    else if (!insideLitteral && chars[i] == ';' && i<chars.length-1)
                        return true;
                }
            }
        }
        return false;
    }
}
