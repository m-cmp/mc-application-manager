package kr.co.mcmp.util;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.XML;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;


public class XMLUtil { 
	private static Logger log = LoggerFactory.getLogger(XMLUtil.class);

	public static Document getNewDocument() {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = null;
		Document doc = null;
		try {
			db = dbf.newDocumentBuilder();
			doc = db.newDocument();
		} catch (ParserConfigurationException e) {
			log.error("", e);
		}

		return doc;
	}

	public static void removeAll(Node node) {
		NodeList nodeList = node.getChildNodes();
		for (int i = nodeList.getLength()-1; i >= 0 ; i--) {
			node.removeChild(nodeList.item(i));
		}
	}

	public static Document getDocument(File documentFile) {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = null;
		Document doc = null;
		try {
			db = dbf.newDocumentBuilder();
			doc = db.parse(documentFile);
		} catch (Exception e) {
			log.error("", e);
		}

		return doc;
	}

	public static Document getDocument(String xmlString) {
		try {
			return getDocument(new ByteArrayInputStream(xmlString.getBytes("utf-8")));
		} catch (Exception e) {
			log.error("", e);
		}
		return null;		
	}
	
	public static Document getDocument(InputStream is) {
		DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
		DocumentBuilder db = null;
		Document doc = null;
		try {
			db = dbf.newDocumentBuilder();
			doc = db.parse(new InputSource(is));

		} catch (Exception e) {
			log.error("", e);
		}

		return doc;
	}

	public static String wrtieXMLString(Document newDoc) {
		String contentString = null;
		String charset = "UTF-8";
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		PrintWriter writer;
		try {
			writer = new PrintWriter(new OutputStreamWriter(bos, "UTF-8"));
			writer.println(MessageFormat.format("<?xml version=\"1.0\" encoding=\"{0}\" standalone=\"yes\" ?>", charset));
			writer.flush();

			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.transform(new DOMSource(newDoc), new StreamResult(bos));

			byte[] data = bos.toByteArray();

			contentString = new String(data, 0, data.length);

		} catch (UnsupportedEncodingException e) {
			log.error("", e);
		} catch (TransformerException e) {
			log.error("", e);
		} finally {
			try {
				if (bos != null)
					bos.close();
			} catch (IOException e) {
			}
		}

		return contentString;
	}

	public static void writeXML(File documentFile, Document newDoc) {

		String charset = "UTF-8";
		FileOutputStream fos = null;
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		PrintWriter writer;
		try {
			writer = new PrintWriter(new OutputStreamWriter(bos, "UTF-8"));
			writer.println(MessageFormat.format("<?xml version=\"1.0\" encoding=\"{0}\" standalone=\"yes\" ?>", charset));
			if (documentFile.getName().contains("library_server") || documentFile.getName().contains("library_local"))
				writer.println("" + "<!DOCTYPE library-groups [\n" + "\t<!ATTLIST group\n " + "\t\tid ID #REQUIRED\n"
						+ "\t\ttype CDATA #REQUIRED\n" + "\t>\n" + "]>");
			writer.flush();

			TransformerFactory transformerFactory = TransformerFactory.newInstance();
			Transformer transformer = transformerFactory.newTransformer();
			transformer.setOutputProperty(OutputKeys.INDENT, "yes");
			transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4");
			transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
			transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
			transformer.transform(new DOMSource(newDoc), new StreamResult(bos));

			byte[] data = bos.toByteArray();

			if (!documentFile.getParentFile().isDirectory())
				documentFile.getParentFile().mkdirs();
			
			fos = new FileOutputStream(documentFile);
			fos.write(data);
			fos.flush();

		} catch (Exception e) {
			log.error("", e);
		} finally {
			try {
				if (bos != null)
					bos.close();
				if (fos != null)
					fos.close();
			} catch (IOException e) {
			}
		}
	}

	public static Element getElementByName(Element parent, String name) {
		if(parent.getElementsByTagName(name).getLength() > 0) {
			return (Element) parent.getElementsByTagName(name).item(0);
		}
		return null;
	}
	
	public static String getElementTextContent(Element parent, String name) {
		Element element = getElementByName(parent, name);
		if(element != null) {
			return element.getTextContent();
		}
		return null;
	}
	
	public static String getAttribute(Element element, String attrName) {
		return element.getAttribute(attrName);
	}
	
	public static String getXPathValue(String xmlContents, String nodeXPath) throws Exception {
		InputSource is = new InputSource(new StringReader(xmlContents));
        Document document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(is);         
          
        
        String value = null; 
        // xpath 생성
        XPath xpath = XPathFactory.newInstance().newXPath();
        NodeList cols = (NodeList)xpath.evaluate(nodeXPath, document, XPathConstants.NODESET);
        if(cols.getLength() > 0) {
        	value = cols.item(0).getTextContent();
        }
        return value;
	}
	
	public static Node getXPathNode(Document doc, String nodeXPath) throws XPathExpressionException {
		XPath xPath = XPathFactory.newInstance().newXPath();
		Node node = (Node)xPath.evaluate(nodeXPath,
				doc.getDocumentElement(), XPathConstants.NODE);
		return node;
	}
	
	public static Element getDirectChild(Element parent, String name) {
		for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
			if (child instanceof Element && name.equals(child.getNodeName()))
				return (Element) child;
		}
		return null;
	}
	
	public static String xmlToJson(String XmlStr) {
		String jsonPrettyPrintString = "";
		try {
			JSONObject xmlJSONObj = XML.toJSONObject(XmlStr);
			jsonPrettyPrintString = xmlJSONObj.toString(4);
		} catch (JSONException je) {
			je.printStackTrace();
		}
		return jsonPrettyPrintString;
	}
	
	public static String jsonToXml(String JsonStr) {
		JSONObject json = new JSONObject(JsonStr);
		String xml = XML.toString(json);
		return xml;
	}

}
