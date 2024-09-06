package kr.co.mcmp.manifest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.kubernetes.client.util.Yaml;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

public class K8S {

	private static Logger logger = LoggerFactory.getLogger(K8S.class);

	public static final String podAntiAffinityKey = "devops.type";
	
	public static enum Controller {
		Pod("apps/v1", "dep"),
		Deployment("apps/v1", "dep"),
		DaemonSet("apps/v1", "dep"),
		StatefulSet("apps/v1", "dep"),
		CronJob("batch/v1beta1", "dep"),
		Rollout("argoproj.io/v1alpha1", "dep");
		
		String apiVersion;
		
		String postfix;
		
		private Controller(String apiVersion, String postfix) {
			this.apiVersion = apiVersion;
			this.postfix = postfix;
		}
		
		public String getApiVersion() {
			return this.apiVersion;
		}
		
		public String getPostfix() {
			return this.postfix;
		}


	}
	
	public static enum StrategyType {
		RollingUpdate,
		Recreate
	}
	
//	public static enum VolumeType {
//		HostPath, PVC
//	}
	
	public static enum Kind {
		Service("v1", "svc"),
		Ingress("networking.k8s.io/v1beta1", "ing"),
		ConfigMap("v1", "cfm"),
		Secret("v1", "sec"),
		TlsSecret("v1", "httptls"),
		HorizontalPodAutoscaler("autoscaling/v2beta2", "hpa"),
		HTTPProxy("projectcontour.io/v1", "htp"),
		PersistentVolumeClaim("v1", "pvc")
		;
		
		
		
		String apiVersion;
		
		String postfix;
		
		private Kind(String apiVersion, String postfix) {
			this.apiVersion = apiVersion;
			this.postfix = postfix;
		}
		
		public String getApiVersion() {
			return this.apiVersion;
		}
		
		public String getPostfix() {
			return this.postfix;
		}
		
	}

	

	
	
	
	public static enum VolumeType2 {
		DirectoryOrCreate,
		File
	}
	
	public static Map<String, String> getIngressRewriteAnnotaions() {
		
		Map<String, String> map = new LinkedHashMap<> ();
		map.put("nginx.ingress.kubernetes.io/rewrite-target", "/$2");
		
		return map;
		
	}
	
	
	public static void printJson(String title, Object object) {

		if (object == null) {
			logger.info("[{}] null", title);
			return;
		}

		try {
			ObjectMapper mapper = new ObjectMapper();
			JsonNode jsonNode = mapper.convertValue(object, JsonNode.class);
			logger.info("[{}] {}", title, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(jsonNode));
		} catch (Exception e) {
			logger.info("[{}] {}", title, e.getMessage());
		}
	}
	
	
	public static void printYaml(String title, Object object) {
		
		if (object == null) {
			logger.info("[{}] null", title);
			return;
		}

		logger.info("[{}] {}", title, Yaml.dump(object));
	}
	
}
