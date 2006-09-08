package velosurf.test;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.StringTokenizer;

import gnu.regexp.RE;
import gnu.regexp.REException;
import gnu.regexp.REMatch;

import org.jdom.Comment;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.Namespace;
import org.jdom.output.XMLOutputter;

public class def2xml
{
    public static void main(String args[]) {
        try {
            
            if (args.length != 2) {
                System.out.println("def2xml converter\n\tUsage : java velosurf.test.def2xml <input.def> <output.xml>");
            }
            
            Element database = new Element("database");
            Document document = new Document(database);
            
            File inputFile = new File(args[0]);
            File outputFile = new File(args[1]);
            
            BufferedReader input = new BufferedReader(new FileReader(inputFile));
            BufferedWriter output = new BufferedWriter(new FileWriter(outputFile));
            
            RE reLine = new RE("^([^=\\.]+)(?:\\.([^=]+))?\\s*=\\s*(.*)$");
            RE reValue = new RE("^(\\*|!|-)?\\s*(\\w+)?\\s*(?:\\(([^\\)]*)\\))?\\s*(?::\\s*(.*))?$");

            String line;
            while ((line=input.readLine())!=null) {
                line = line.trim();
                if (line.length()==0) continue;
                if (line.charAt(0)=='#') {
                    // comment
                    while (line.charAt(0)=='#' || line.charAt(0)==' ') line=line.substring(1);
                    database.addContent(new Comment(line));
//System.out.println("comment="+line);
                }
                else {
                    REMatch keyval = reLine.getMatch(line);
                    if (keyval == null) {
                        System.out.println("warning : ignored line :\n\t"+line);
                        continue;
                    }
                    String key = keyval.getSubStartIndex(1)==-1?null:keyval.toString(1);
                    String subkey = keyval.getSubStartIndex(2)==-1?null:keyval.toString(2);
                    String value = keyval.getSubStartIndex(3)==-1?null:keyval.toString(3);
                    
                    if (subkey==null) {
                        // class
                        Element entity = findEntity(database,key);
                        if (entity == null) {
                            entity = new Element("entity");
                            entity.setAttribute("name",key);
                            database.addContent(entity);
                        }
                        entity.setAttribute("class",value);
//System.out.println("class :\n\tkey="+key+"\n\tclass="+value);
                    }
                    else {
                        // attribute
                        REMatch attrib = reValue.getMatch(value);
                        if (attrib == null) {
                            System.out.println("error in attrib definition :\n\tattrib="+key+"."+subkey+"\n\tvalue="+value);
                            continue;
                        }
                        String cardinality = attrib.getSubStartIndex(1)==-1?null:attrib.toString(1);
                        String entity = attrib.getSubStartIndex(2)==-1?null:attrib.toString(2);
                        String arguments = attrib.getSubStartIndex(3)==-1?null:attrib.toString(3);
                        String query = attrib.getSubStartIndex(4)==-1?null:attrib.toString(4);
                        
                        Element parent;
                        if (key.equals("db")) parent = database;
                        else {
                            parent = findEntity(database,key);
                            if (parent == null) {
                                parent = new Element("entity");
                                parent.setAttribute("name",key);
                                database.addContent(parent);
                            }
                            Element attribute = new Element("attribute");
                            attribute.setAttribute("name",subkey);
                            parent.addContent(attribute);
                            if (cardinality == null) {
                                if (entity == null) attribute.setAttribute("result","row");
                                else attribute.setAttribute("result","row/"+entity);
                            }
                            else if (cardinality.equals("!")) attribute.setAttribute("result","scalar");
                            else if (cardinality.equals("*")) {
                                if (entity == null) attribute.setAttribute("result","rowset");
                                else attribute.setAttribute("result","rowset/"+entity);
                            }
                            else System.out.println("Warning : Unknown cardinality '"+cardinality+"' in line\n\t"+line);
                            
                            if (arguments == null) {
                                if (query == null) System.out.println("Warning : neither arguments or query specified in line\n\t"+line);
                                else attribute.addContent(query);
                            }
                            else {
                                StringTokenizer tokenizer = new StringTokenizer(arguments,",");
                                ArrayList params = new ArrayList();
                                while (tokenizer.hasMoreTokens())
                                    params.add(tokenizer.nextToken());
                                if (query == null) {
                                    if (params.size()==1) attribute.setAttribute("foreign-key",(String)params.get(0));
                                    else System.out.println("Warning : one argument expected in query-less attribute in line\n\t"+line);
                                }
                                else {
                                    int qmark,quote,lastindex=0,index=0;
                                    int paramindex=0;
                                    int quotenb = 0;
                                    String parsequery = query;
                                    while (index!=-1) {
                                        qmark=parsequery.indexOf('?',lastindex);
                                        quote=parsequery.indexOf('\'',lastindex);
                                        index=-1;
                                        if (qmark>=0 && quote>0) index=Math.min(qmark,quote);
                                        else if (qmark==-1) index=quote;
                                        else if (quote==-1) index=qmark;
                                        if (index==-1) attribute.addContent(parsequery);
                                        else {
                                            if (index==qmark && quotenb%2==0) {
                                                attribute.addContent(parsequery.substring(0,index));
                                                if (paramindex+1>params.size()) {
                                                    System.out.println("Warning : too few arguments for prepared query in line\n\t"+line);
                                                    continue;
                                                }
                                                attribute.addContent(new Element((String)params.get(paramindex++)));
                                                parsequery = parsequery.substring(index+1);
                                            }
                                            else if (index==quote) quotenb++;
                                        }
                                    }
                                }
                            }
                            attribute.setAttribute("space","preserve",Namespace.XML_NAMESPACE);

                        }
//System.out.println("attribute :\n\tcardinality="+cardinality+"\n\tentity="+entity+"\n\targuments="+arguments+"\n\tquery="+query);
                    }
                }
            }
            
            XMLOutputter xmlout = new XMLOutputter("    ",true);
            xmlout.setTextNormalize(true);
            xmlout.output(document,output);
        }
        catch (Exception e) {
            System.out.println("Exception in def2xml !");
            e.printStackTrace();
        }
    }
    
    public static Element findEntity(Element database,String name) {
        Iterator children = database.getChildren("entity").iterator();
        while (children.hasNext()) {
            Element entity = (Element)children.next();
            String name2 = entity.getAttributeValue("name");
            if (name2 != null && name2.equals(name)) return entity;
        }
        return null;
    }
}

