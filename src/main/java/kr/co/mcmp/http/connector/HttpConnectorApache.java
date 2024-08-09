package kr.co.mcmp.http.connector;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpStatus;
import org.apache.http.NameValuePair;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.ssl.SSLConnectionSocketFactory;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;

public class HttpConnectorApache extends AbstractConnector {
	private CloseableHttpClient httpClient = null;
	private CloseableHttpResponse httpResponse = null;
	
	private int timeout;
	private int resultTimeout;
	private boolean handleRedirect;
	private String url;
	private String method;
	private String encoding;
	private String postEntity;
	private Map<String, String> configurations;
	private Map<String, String> requestParam;
	
	public HttpConnectorApache(
			String url, 
			String method, 
			String encoding, 
			String postEntity,
			Map<String, String> configurations, 
			Map<String, String> requestParam,
			int connectTimeout, 
			int resultTimeout,
			boolean handleRedirect) {
		super();		
		this.url = url;
		this.method = method;
		this.encoding = encoding;
		this.postEntity = postEntity;
		this.configurations = configurations;
		this.requestParam = requestParam;
		this.timeout = connectTimeout;
		this.resultTimeout = resultTimeout;
		this.handleRedirect = handleRedirect;
	}
	

	@Override
	public Object connect() {
		try {
			if(url!=null && url.length()>5 && url.substring(0,5).equals("https")){
	            httpClient = getHttpsClient();
	        } else {
	            httpClient = getHttpClient();
	        }
			
			HttpUriRequest uriRequest = null;
			RequestConfig rquestConfig = createRequestConfig(timeout, resultTimeout, handleRedirect);
			if(method.toLowerCase().equals("post")) {
				uriRequest = createPostConnect(url, configurations, requestParam, postEntity, encoding, rquestConfig);
			} else if(method.toLowerCase().equals("get")) {
				uriRequest = createGetConnect(url, configurations, requestParam, rquestConfig);
			}
			return execute(httpClient, uriRequest, encoding);
		} catch (Exception e) {
			e.printStackTrace();
			onReceiveResult(IConnectionResultReceiver.CONNECTION_FAIL, e);
		}

		return null;
	}

	@Override
	public boolean disconnect() {
		return false;
	}
	
	/**
	 * 
	 * @param httpClient
	 * @param uriRequest
	 * @param charset
	 * @throws Exception
	 */
	private Object execute(CloseableHttpClient httpClient, HttpUriRequest uriRequest, String charset) throws Exception {
		httpResponse = httpClient.execute(uriRequest);
        if(httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_OK 
        		|| httpResponse.getStatusLine().getStatusCode() == HttpStatus.SC_MOVED_TEMPORARILY) {
             HttpEntity resEntity = httpResponse.getEntity();
             if (resEntity != null) {
             	
             	InputStreamReader bis = new InputStreamReader(resEntity.getContent(), charset);
     			BufferedReader reader = new BufferedReader(bis);
     			
     			StringBuffer buffer = new StringBuffer();
     			String str = null;
     			while((str = reader.readLine())!= null) {
     				buffer.append(str);
     			}     			
     			if(bis != null) {
     				bis.close();
     			}     			
     			onReceiveResult(IConnectionResultReceiver.RESULT_OK, buffer.toString());
     			return buffer.toString();
             }
             onReceiveResult(IConnectionResultReceiver.CONNECTION_OK);
        } else {
        	onReceiveResult(IConnectionResultReceiver.CONNECTION_FAIL);
        }
        return null;
	}
	
	public String getResponseHeader(String key) {
		Header[] headers = httpResponse.getAllHeaders();
		for(Header h : headers) {
			if(h.getName().equals(key)) {
				return h.getValue();
			}
		}
		return null;
	}
	
	
	/**
	 * post용 커넥터 생성
	 * @param url
	 * @param configurations
	 * @param requestParam
	 * @param entity
	 * @param charset
	 * @return
	 * @throws Exception
	 */
	private HttpUriRequest createPostConnect(
			String url, 
			Map<String, String> configurations, 
			Map<String, String> requestParam, 
			String entity, 
			String charset,
			RequestConfig requestConfig) throws Exception{
		
        HttpPost httpPost = getHttpPost(url);
        
        if(requestConfig != null) {
            httpPost.setConfig(requestConfig);
        }
        
        // 추가 요청 헤더를 넣음
        if(configurations != null && configurations.size() > 0) {
        	Iterator<String> iter = configurations.keySet().iterator();
        	while(iter.hasNext()) {
        		String key = iter.next();
        		String value = configurations.get(key);
        		
        		 httpPost.addHeader(key, value);
        	}
        }
        
        //요청 파라메타 적용
        //entity 가 null이 아닌 경우 entity를 보낸다.
        if(entity != null && !entity.equals("")) {
        	httpPost.setEntity(new StringEntity(entity, charset));
        } else {
        	List<NameValuePair> nvps = new ArrayList<NameValuePair>();
            if(requestParam != null && requestParam.size() > 0) {                
                Iterator<String> iter = requestParam.keySet().iterator();
            	while(iter.hasNext()) {
            		String key = iter.next();
            		String value = requestParam.get(key);
            		
            		nvps.add(new BasicNameValuePair(key, value));
            	}
            }
            httpPost.setEntity(new UrlEncodedFormEntity(nvps, charset));
        }
        
        return httpPost;
    }
	
	/**
	 * get용 커넥터 생성
	 * @param url
	 * @param configurations
	 * @param requestParam
	 * @return
	 * @throws Exception
	 */
	private HttpUriRequest createGetConnect(
			String url, 
			Map<String, String> configurations, 
			Map<String, String> requestParam, 
			RequestConfig requestConfig) throws Exception {
		
		String paramStr = getRequestParameterStr(requestParam);
		String newUrl = url;
		if(paramStr.length() > 0) {
			newUrl = newUrl + "?" + paramStr;
		}
		
        HttpGet httpGet = getHttpGet(newUrl);        
        if(requestConfig != null) {
        	httpGet.setConfig(requestConfig);
        }
        
        // 추가 요청 헤더를 넣음
        if(configurations != null && configurations.size() > 0) {
        	Iterator<String> iter = configurations.keySet().iterator();
        	while(iter.hasNext()) {
        		String key = iter.next();
        		String value = configurations.get(key);
        		httpGet.addHeader(key, value);
        	}
        }
        return httpGet;
    }
	
	private RequestConfig createRequestConfig(int connectTimeout, int resultTimeout, boolean redirect) {
		RequestConfig requestConfig = RequestConfig.custom()
				.setConnectionRequestTimeout(resultTimeout)
				.setConnectTimeout(connectTimeout)
		        .setSocketTimeout(connectTimeout)
		        .setRedirectsEnabled(redirect).build();
		return requestConfig;
	}
	
	private CloseableHttpClient getHttpClient(){
        return HttpClients.createDefault();
    }

    private CloseableHttpClient getHttpsClient() throws Exception {
        SSLContext sslContext = SSLContext.getInstance("SSL");
        sslContext.init(null, new TrustManager[] { new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() {
                return null;
            }
            public void checkClientTrusted(X509Certificate[] certs,String authType) {
            }
            public void checkServerTrusted(X509Certificate[] certs,String authType) {
            }
        } }, new SecureRandom());
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext);
        CloseableHttpClient httpClient = HttpClients.custom().setSSLSocketFactory(sslsf).build();
        return httpClient;
    }

	private HttpPost getHttpPost(String url){
        return new HttpPost(url);
    }

    private HttpGet getHttpGet(String url){
        return new HttpGet(url);
    }
	
    
    /**
	 * 리퀘스트 파라메타를 URL 전송용 스트링으로 변환하여 리턴한다.
	 * @return
	 */
	public String getRequestParameterStr(Map<String, String> requestParam) {
		StringBuffer sb = new StringBuffer();
		if(requestParam != null) {
			
			Iterator<String> iter = requestParam.keySet().iterator();
        	while(iter.hasNext()) {
        		String key = iter.next();
        		String value = requestParam.get(key);
        		
        		sb.append(key);
				sb.append("=");
				sb.append(value);
				sb.append("&");
				
				if(iter.hasNext()) {
					sb.append("&");
				}
				
        	}
		}		
		return sb.toString();
	}
	
}
