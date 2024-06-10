package m.cmp.appManager.jenkins.model;

import m.cmp.appManager.api.response.ResponseCode;
import m.cmp.appManager.exception.McmpException;
import m.cmp.appManager.k8s.model.K8SConfig;
import m.cmp.appManager.oss.model.Oss;
import m.cmp.appManager.util.AES256Util;
import m.cmp.appManager.util.NamingUtils;
import lombok.Getter;
import lombok.ToString;
import org.apache.commons.lang3.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;


@Getter
@ToString
public class JenkinsCredential {
	
	public static final String CREDENTIALS_USERNAME_PASSWORD = "usernamePassword";
	public static final String CREDENTIALS_SECRET_TEXT = "secretText";

    private String description;

    private String displayName;

    private String fullName;

    private String id;

    private String typeName;
    
    public static String getCredentialTypeByOss(String ossCd) {
    	if ( StringUtils.equals("CLUSTER", ossCd) ) {
    		return CREDENTIALS_SECRET_TEXT;
    	}
    	else {
    		return CREDENTIALS_USERNAME_PASSWORD;
    	}
    }
    
    public static String createCredentialXml(Oss oss, K8SConfig k8s, String credentialType) {
        try {
            // XML 문서 파싱
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder documentBuilder = factory.newDocumentBuilder();

            Document document = null;
            if ( StringUtils.equals(CREDENTIALS_USERNAME_PASSWORD, credentialType) ) {
            	document = createUsernamePasswordCredentialXml(documentBuilder, oss);
            }
            else if ( StringUtils.equals(CREDENTIALS_SECRET_TEXT, credentialType) ) {
            	document = createClusterSecretTextCredentialXml(documentBuilder, k8s);
            }

            // XML 문자열로 변환하기! //
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            DOMSource source = new DOMSource(document);
            StreamResult result = new StreamResult(out);

            TransformerFactory transFactory = TransformerFactory.newInstance();
            Transformer transformer = transFactory.newTransformer();

            transformer.setOutputProperty(OutputKeys.ENCODING, "UTF-8");
            transformer.setOutputProperty(OutputKeys.INDENT, "yes");// 들여 쓰기
            transformer.transform(source, result);

            return new String(out.toByteArray(), StandardCharsets.UTF_8);

        } catch (ParserConfigurationException e) {
            throw new McmpException(ResponseCode.UNKNOWN_ERROR, e.getMessage());
        } catch (TransformerException e) {
            throw new McmpException(ResponseCode.UNKNOWN_ERROR, e.getMessage());
        }
    }

    public static Document createUsernamePasswordCredentialXml(DocumentBuilder documentBuilder, Oss credentialOss) {
    	String decryptPassword = null;
		if ( StringUtils.isNoneBlank(credentialOss.getOssPassword()) ) {
			decryptPassword = AES256Util.decrypt(credentialOss.getOssPassword());
		}
		
        // 새로운 XML 생성! //
        // 새로운 Document 객체 생성
        Document document = documentBuilder.newDocument();

        Element root = document.createElement("com.cloudbees.plugins.credentials.impl.UsernamePasswordCredentialsImpl");
        Element scope = document.createElement("scope");
        scope.setTextContent("GLOBAL");
        Element id = document.createElement("id");
    	id.setTextContent(NamingUtils.getCredentialName(credentialOss.getOssId(), credentialOss.getOssName()));
        Element username = document.createElement("username");
        username.setTextContent(credentialOss.getOssUsername());
        Element password = document.createElement("password");
        password.setTextContent(decryptPassword);
    	Element description = document.createElement("description");
    	description.setTextContent(credentialOss.getOssUrl());

        root.appendChild(scope);
        root.appendChild(id);
        root.appendChild(username);
        root.appendChild(password);
    	root.appendChild(description);

        document.appendChild(root);

        return document;
    }
    
    public static Document createClusterSecretTextCredentialXml(DocumentBuilder documentBuilder, K8SConfig k8s) {		
		// 새로운 XML 생성! //
		// 새로운 Document 객체 생성
		Document document = documentBuilder.newDocument();
		
		Element root = document.createElement("org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl");
		Element scope = document.createElement("scope");
		scope.setTextContent("GLOBAL");
		Element id = document.createElement("id");
    	id.setTextContent(NamingUtils.getCredentialName(k8s.getK8sId(), k8s.getK8sName()));
		Element secret = document.createElement("secret");
		secret.setTextContent(k8s.getContent());
    	Element description = document.createElement("description");
    	description.setTextContent(k8s.getK8sName());
		
		root.appendChild(scope);
		root.appendChild(id);
		root.appendChild(secret);
    	root.appendChild(description);
		
		document.appendChild(root);

		return document;
    }
}
