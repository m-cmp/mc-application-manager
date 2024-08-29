package kr.co.mcmp.manifest;

import com.google.gson.annotations.SerializedName;
import io.kubernetes.client.common.KubernetesObject;
import io.kubernetes.client.custom.IntOrString;
import io.kubernetes.client.custom.Quantity;
import io.kubernetes.client.openapi.models.*;
import io.kubernetes.client.util.Yaml;
import kr.co.mcmp.manifest.k8s.*;

import org.apache.commons.lang3.StringUtils; // lang -> lang3
//import org.springframework.util.StringUtils;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class K8SDeployYamlGenerator {

	private V1Pod getPod(K8SPodDTO podDto) {

		V1Pod pod = new V1Pod();
		pod.setApiVersion(K8S.Controller.Pod.getApiVersion());
		pod.setKind("Pod");

		V1ObjectMeta metadata = new V1ObjectMeta();
		metadata.setName(podDto.getPodName());
		metadata.setNamespace(podDto.getNamespace());
		metadata.setLabels(podDto.getLabels());
		pod.setMetadata(metadata);

		V1PodSpec podSpec = getPodSpec(podDto);
		pod.setSpec(podSpec);

		return pod;
	}

	private V1PodSpec getPodSpec(K8SPodDTO podDto){
		V1PodSpec podSpec = new V1PodSpec();
		List<V1Container> containerList = podDto.getContainers();
		podSpec.setContainers(containerList);
		podSpec.setRestartPolicy(podDto.getRestartPolicy());
		return podSpec;
	}


	public String getPodYaml(K8SPodDTO podDto) {
		V1Pod pod = getPod(podDto);
		StringBuffer buffer = new StringBuffer();
		appendYaml(buffer, pod);
		String yaml = buffer.toString();
		return yaml;
	}


	private V1Deployment getDeployment(K8SDeploymentDTO deploy) {

		V1Deployment deployment = new V1Deployment();
		deployment.setApiVersion("apps/v1");
		deployment.setKind("Deployment");

		V1ObjectMeta metadata = new V1ObjectMeta();
		metadata.setName(deploy.getName());
		metadata.setNamespace(deploy.getNamespace());
		metadata.setLabels(deploy.getLabels());
		deployment.setMetadata(metadata);

		// spec
		V1DeploymentSpec deploymentSpec = new V1DeploymentSpec();
		deployment.setSpec(deploymentSpec);

		// spec.selector
		V1LabelSelector labelSelector = new V1LabelSelector();
		labelSelector.setMatchLabels(deploy.getLabels());
		deploymentSpec.setSelector(labelSelector);

		// spec.replicas
		Integer replicas = (deploy.getReplicas() == null || deploy.getReplicas() == 0) ? 1 : deploy.getReplicas();
		deploymentSpec.setReplicas(replicas);

		// spec.strategy
		V1DeploymentStrategy deploymentStrategy = new V1DeploymentStrategy();
		deploymentStrategy.setType("RollingUpdate");
		deploymentSpec.setStrategy(deploymentStrategy);

		// template(pod)
		deploymentSpec.setTemplate(getPodTemplateSpec(deploy));
		return deployment;
	}


	private V1PodTemplateSpec getPodTemplateSpec(K8SDeploymentDTO deploy){

		V1PodTemplateSpec podTemplateSpec = new V1PodTemplateSpec();
		V1ObjectMeta metadata = new V1ObjectMeta();
		metadata.setLabels(new HashMap<String, String>(deploy.getLabels()));
		podTemplateSpec.setMetadata(metadata);

		K8SPodDTO podDto = deploy.getPodDto();
		V1PodSpec podSpec = getPodSpec(podDto);
/*
		// add imagepullsecrets
		if (StringUtils.isNotEmpty(deploy.getImagePullSecret())) {
			List<V1LocalObjectReference> imagePullSecretsList = new ArrayList<V1LocalObjectReference>(1);
			V1LocalObjectReference v1LocalObjectReference = new V1LocalObjectReference();
			v1LocalObjectReference.setName(deploy.getImagePullSecret());
			imagePullSecretsList.add(v1LocalObjectReference);
			podSpec.setImagePullSecrets(imagePullSecretsList);
		}
*/
		podTemplateSpec.setSpec(podSpec);


/*
		// template.spec.container.envFrom.configMapRef
		if (podDto.getConfigMapData() != null && !podDto.getConfigMapData().isEmpty()) {
			V1ConfigMapEnvSource configMapEnvSource = new V1ConfigMapEnvSource();
			configMapEnvSource.setName(getConfigMapName(deploy.getName()));
			V1EnvFromSource fromSource = new V1EnvFromSource();
			fromSource.setConfigMapRef(configMapEnvSource);
			container.addEnvFromItem(fromSource);
		}

		if (deploy.getSecretData() != null && !deploy.getSecretData().isEmpty()) {
			V1SecretEnvSource secretEnvSource = new V1SecretEnvSource();
			secretEnvSource.setName(getSecretName(deploy.getName()));
			V1EnvFromSource secretFromSource = new V1EnvFromSource();
			secretFromSource.setSecretRef(secretEnvSource);
			container.addEnvFromItem(secretFromSource);
		}
*/

		// template.spec.container.volumeMounts
		setVolumeMounts(podSpec.getContainers().get(0), deploy.getVolumes());

		// template.spec.container.resources
		//setResource(container, deploy.getResource());
/*
		//startup
		V1Probe startupProbe = new V1Probe();
		startupProbe.setInitialDelaySeconds(30);
		startupProbe.setPeriodSeconds(10);
		V1TCPSocketAction startupSocket = new V1TCPSocketAction();
		startupSocket.setPort(new IntOrString(containerPort));
		startupProbe.setTcpSocket(startupSocket);

		container.setStartupProbe(startupProbe);

		//readiness
		V1Probe readinessProbe = new V1Probe();
		readinessProbe.setInitialDelaySeconds(30);
		readinessProbe.setPeriodSeconds(10);
		V1TCPSocketAction readinessSocket = new V1TCPSocketAction();
		readinessSocket.setPort(new IntOrString(containerPort));
		readinessProbe.setTcpSocket(readinessSocket);

		container.setReadinessProbe(readinessProbe);

		//liveness
		V1Probe livenessProbe = new V1Probe();
		livenessProbe.setInitialDelaySeconds(30); livenessProbe.setPeriodSeconds(10);
		V1TCPSocketAction livenessSocket = new V1TCPSocketAction();
		livenessSocket.setPort(new IntOrString(containerPort));
		livenessProbe.setTcpSocket(livenessSocket);

		container.setLivenessProbe(livenessProbe);
*/
		// template.spec.volume
		//setHostPathVolumes(podSpec, deploy.getHostPathVolumes());
		//setPVCVolumes(podSpec, deploy.getPvcVolumes());

		return podTemplateSpec;
	}


	public String getDeploymentYaml(K8SDeploymentDTO deploy){
		V1Deployment deployment = getDeployment(deploy);
		StringBuffer buffer = new StringBuffer();
		appendYaml(buffer, deployment);
		String yaml = buffer.toString();
		return yaml;
	}



	private V1ConfigMap getConfigmap(K8SConfigmapDTO cmDto){
		return null;
	}

	public String getConfigmapYaml(K8SConfigmapDTO cmDto){
		return "";
	}


	private V1HorizontalPodAutoscaler getHpa(K8SHPADTO hpaDto){

		V1HorizontalPodAutoscaler hpa = new V1HorizontalPodAutoscaler();

		hpa.setApiVersion("autoscaling/v2");
		hpa.setKind("HorizontalPodAutoscaler");

		V1ObjectMeta metadata = new V1ObjectMeta();
		metadata.setName(hpaDto.getHpaName());
		metadata.setNamespace(hpaDto.getNamespace());
		metadata.setLabels(hpaDto.getLabels());
		hpa.setMetadata(metadata);

		V1HorizontalPodAutoscalerSpec spec = new V1HorizontalPodAutoscalerSpec();

		V1CrossVersionObjectReference ref = new V1CrossVersionObjectReference();
		ref.setApiVersion("apps/v1");
		ref.setKind(hpaDto.getTarget().getKind());
		ref.setName(hpaDto.getTarget().getName());
		spec.setScaleTargetRef(ref);

		spec.setMinReplicas(hpaDto.getMinReplicas());
		spec.setMaxReplicas(hpaDto.getMaxReplicas());

		V2beta1MetricSpec metrics = new V2beta1MetricSpec();
		metrics.setType(hpaDto.getMetric().getType()); // Resource
		V2beta1ResourceMetricSource resource = new V2beta1ResourceMetricSource();
		resource.setName(hpaDto.getMetric().getResourceName());
		resource.setTargetAverageUtilization(hpaDto.getMetric().getTargetAverageUtilization());
		//V2beta2MetricTarget target = new V2beta2MetricTarget();
		//target.setType(hpaDto.getMetric().getTargetType());
		//target.setAverageUtilization(hpaDto.getMetric().getTargetAverageUtilization());

		metrics.setResource(resource);



		return hpa;
	}

	public String getHpaYaml(K8SHPADTO hpaDto){
		V1HorizontalPodAutoscaler hpa = getHpa(hpaDto);
		StringBuffer buffer = new StringBuffer();
		appendYaml(buffer, hpa);
		String yaml = buffer.toString();
		return yaml;
	}


	private V1Service getSvc(K8SServiceDTO svcDto){
		return null;
	}

	public String getSvcYaml(K8SServiceDTO svcDto){
		return "";
	}


	private V1ObjectMeta getMetadata(String name, String namespace) {
		V1ObjectMeta metadata = new V1ObjectMeta();
		metadata.setName(name);
		metadata.setNamespace(namespace);
		return metadata;
	}


//	public String generateTlsSecret(K8SDeployDTO deploy) {
//		
//		StringBuffer buffer = new StringBuffer();
//		appendYaml(buffer, getTlsSecret(deploy));
//		buffer.append(deploy.getDeployYaml());
//		String yaml = buffer.toString();
//		logger.info("[generateYaml]{}", yaml);
//		return yaml;
//	}

	boolean isEmpty(Map<String, String> map) {

		if (map == null)
			return true;
		if (map.containsKey(""))
			map.remove("");
		return map.isEmpty();
	}

	boolean isEmpty(List<String> list) {

		if (list == null)
			return true;

		List<String> temp = new ArrayList<String>();
		temp.addAll(list);

		for (String str : temp) {
			if (StringUtils.isBlank(str)) {
				list.remove(str);
			}
		}

		return list.isEmpty();
	}

	private void appendYaml(StringBuffer buffer, Object object) {

		if (object == null) {
			return;
		}

		buffer.append("---\n");
		buffer.append(Yaml.dump(object));
	}

	// Deployment







	// DaemonSet
	private V1DaemonSet getDaemonSet(K8SDeployDTO deploy) {

		V1DaemonSet daemonSet = new V1DaemonSet();
		daemonSet.setApiVersion(K8S.Controller.DaemonSet.getApiVersion());
		daemonSet.setKind(deploy.getController());

		// metadata
		V1ObjectMeta metadata = getControllerMetadata(deploy.getController(), deploy.getName(), deploy.getNamespace(), deploy.getLabels());
		daemonSet.setMetadata(metadata);

		// spec
		V1DaemonSetSpec daemonSetSpec = new V1DaemonSetSpec();
		daemonSet.setSpec(daemonSetSpec);

		// spec.selector
		V1LabelSelector labelSelector = new V1LabelSelector();
		labelSelector.setMatchLabels(deploy.getLabels());
		daemonSetSpec.setSelector(labelSelector);

		// spec.updateStrategy
		V1DaemonSetUpdateStrategy updateStrategy = new V1DaemonSetUpdateStrategy();
		updateStrategy.setType(deploy.getStrategyType());
		daemonSetSpec.setUpdateStrategy(updateStrategy);

		// template(pod)
		//daemonSetSpec.setTemplate(getPodTemplateSpec(deploy));
		return daemonSet;

	}

	// StatefulSet
	private V1StatefulSet getStatefulSet(K8SDeployDTO deploy) {

		V1StatefulSet statefulSet = new V1StatefulSet();
		statefulSet.setApiVersion(K8S.Controller.StatefulSet.getApiVersion());
		statefulSet.setKind(deploy.getController());

		// metadata
		V1ObjectMeta metadata = getControllerMetadata(deploy.getController(), deploy.getName(), deploy.getNamespace(), deploy.getLabels());
		statefulSet.setMetadata(metadata);

		// spec
		V1StatefulSetSpec statefulSetSpec = new V1StatefulSetSpec();
		statefulSet.setSpec(statefulSetSpec);

		// spec.selector
		V1LabelSelector labelSelector = new V1LabelSelector();
		labelSelector.setMatchLabels(deploy.getLabels());
		statefulSetSpec.setSelector(labelSelector);

		// spec.replica
		statefulSetSpec.setReplicas(deploy.getReplicas());

		// spec.updateStrategy
		V1StatefulSetUpdateStrategy updateStrategy = new V1StatefulSetUpdateStrategy();
		updateStrategy.setType(deploy.getStrategyType());
		statefulSetSpec.setUpdateStrategy(updateStrategy);

		// sepc.service
		statefulSetSpec.serviceName(getServiceName(deploy));

		// template(pod)
		//statefulSetSpec.template(getPodTemplateSpec(deploy));

		// TODO volumeClaimTemplates

		return statefulSet;
	}

	// CronJob


	private V1ObjectMeta getControllerMetadata(String controller, String name, String namespace,
			Map<String, String> labels) {

		K8S.Controller ctl = K8S.Controller.valueOf(controller);

		V1ObjectMeta metadata = new V1ObjectMeta();
		// metadata.setName(getName(ctl.getPostfix(), name));
		// kbcard 네이밍기준 (서비스그룹-프로젝트영문명-브랜치-dep-망구분-001)
		metadata.setName(getName(ctl.getPostfix(), String.format("%s-001", name)));
		// metadata.namespace
		metadata.setNamespace(namespace);
		// metadata.labels
		metadata.setLabels(new HashMap<String, String>(labels));

		return metadata;
	}


	private String getName(String postfix, String name) {
		return name.replace("KIND", postfix);
		// return String.format("%s-%s", name, postfix.toLowerCase());
	}

	private Map<String, String> addDefaultLabel(Map<String, String> labels , String name) {
		if (labels == null) {
			labels = new HashMap<String, String>();
		}
		
		labels.remove(""); // 화면에서 "" : "" 값이 넘어오는것에 대한 defense
		
		labels.put(K8S.podAntiAffinityKey, name);
		return labels;
	}
	
	
	private Map<String, String> removeDefaultLabel(Map<String, String> labels) {
		if (labels != null) { labels.remove(K8S.podAntiAffinityKey); }
		return labels;
	}

	// VolumeMounts
	private void setVolumeMounts(V1Container container, List<K8SVolume> volumesList) {

		if (volumesList == null || volumesList.size() == 0) {
			return;
		}

		for (K8SVolume k8sVolume : volumesList) {
			V1VolumeMount volumeMount = new V1VolumeMount();
			volumeMount.setName(k8sVolume.getName());
			volumeMount.setMountPath(k8sVolume.getMountPath());
			container.addVolumeMountsItem(volumeMount);
		}

	}

	// HostPathVolumes
	private void setHostPathVolumes(V1PodSpec podSpec, List<K8SVolume> volumesList) {

		if (volumesList == null || volumesList.size() == 0) {
			return;
		}

		for (K8SVolume k8sVolume : volumesList) {
			V1Volume volume = new V1Volume();
			volume.setName(k8sVolume.getName());

			V1HostPathVolumeSource hostPathVolumeSource = new V1HostPathVolumeSource();
			hostPathVolumeSource.setPath(k8sVolume.getHostPath());
			hostPathVolumeSource.setType(k8sVolume.getType());
			volume.setHostPath(hostPathVolumeSource);

			podSpec.addVolumesItem(volume);
		}
	}

	// PVCVolumes
	private void setPVCVolumes(V1PodSpec podSpec, List<K8SVolume> volumesList) {

		// List<K8SVolume> volumesList = deploy.getPvcVolumes();
		if (volumesList == null || volumesList.size() == 0) {
			return;
		}

		for (K8SVolume k8sVolume : volumesList) {
			V1Volume volume = new V1Volume();
			volume.setName(k8sVolume.getName());

			V1PersistentVolumeClaimVolumeSource persistentVolumeClaimVolumeSource = new V1PersistentVolumeClaimVolumeSource();
			persistentVolumeClaimVolumeSource.claimName(k8sVolume.getClaimName());
			volume.setPersistentVolumeClaim(persistentVolumeClaimVolumeSource);

			podSpec.addVolumesItem(volume);
		}
	}

	private void setResource(V1Container container, K8SResource resource) {

		if (resource == null)
			return;

		String requestCpu = resource.getRequestCpu();
		String requestMemory = resource.getRequestMemory();

		if ((StringUtils.isEmpty(requestCpu) || requestCpu.startsWith("0"))
				&& (StringUtils.isEmpty(requestMemory) || requestMemory.startsWith("0"))) {
			return;
		}

		String limitCpu = resource.getLimitCpu();
		String limitMemory = resource.getLimitMemory();

		V1ResourceRequirements resourceRequirements = new V1ResourceRequirements();
		// Map<String, Quantity> requests = new HashMap<String, Quantity>();
		// Map<String, Quantity> limits = new HashMap<String, Quantity>();

		if (!StringUtils.isEmpty(requestCpu) && !requestCpu.startsWith("0")) {
			resourceRequirements.putRequestsItem("cpu", Quantity.fromString(requestCpu));
			limitCpu = !StringUtils.isEmpty(limitCpu) ? limitCpu : requestCpu;
			resourceRequirements.putLimitsItem("cpu", Quantity.fromString(limitCpu));
		}

		if (!StringUtils.isEmpty(requestMemory) && !requestMemory.startsWith("0")) {
			resourceRequirements.putRequestsItem("memory", Quantity.fromString(requestMemory));
			limitMemory = !StringUtils.isEmpty(limitMemory) ? limitMemory : requestMemory;
			resourceRequirements.putLimitsItem("memory", Quantity.fromString(limitMemory));
		}

		container.setResources(resourceRequirements);

	}



	private V1Affinity getPodAntiAffinity(K8SDeployDTO deploy) {

		V1Affinity affinity = new V1Affinity();
		V1PodAntiAffinity podAntiAffinity = new V1PodAntiAffinity();
		affinity.setPodAntiAffinity(podAntiAffinity);

		// lableSelector
		V1LabelSelectorRequirement labelSelectorRequirement = new V1LabelSelectorRequirement();
		labelSelectorRequirement.setKey(K8S.podAntiAffinityKey);
		labelSelectorRequirement.setOperator("In");
		labelSelectorRequirement.addValuesItem(deploy.getDeployName());

		V1LabelSelector labelSelector = new V1LabelSelector();
		labelSelector.addMatchExpressionsItem(labelSelectorRequirement);

		V1PodAffinityTerm term = new V1PodAffinityTerm();
		term.setLabelSelector(labelSelector);
		term.setTopologyKey("kerbernetes.io/hostname");

		podAntiAffinity.addRequiredDuringSchedulingIgnoredDuringExecutionItem(term);

		return affinity;
	}

	// ConfigMap
	private V1ConfigMap getConfigMap(K8SDeployDTO deploy) {

		if (isEmpty(deploy.getConfigMapData())) {
			return null;
		}

		V1ConfigMap configMap = new V1ConfigMap();
		configMap.setApiVersion(K8S.Kind.ConfigMap.getApiVersion());
		configMap.setKind(K8S.Kind.ConfigMap.name());

		//configMap.setMetadata(getMetadata(getConfigMapName(deploy.getName()), deploy.getNamespace()));

		configMap.setData(deploy.getConfigMapData());

		return configMap;

	}

	// Secret
	private V1Secret getSecret(K8SDeployDTO deploy) {

		Map<String, String> secretData = deploy.getSecretData();

		if (isEmpty(secretData)) {
			return null;
		}

		V1Secret secret = new V1Secret();
		secret.setApiVersion(K8S.Kind.Secret.getApiVersion());
		secret.setKind(K8S.Kind.Secret.name());

		//secret.setMetadata(getMetadata(getSecretName(deploy.getName()), deploy.getNamespace()));

		for (String key : secretData.keySet()) {
			secret.putDataItem(key, secretData.get(key).getBytes());
		}
		return secret;

	}

	// TLS Secret
	private V1Secret getTlsSecret(K8SDeployDTO deploy) {

		K8SProxyInfo proxyInfo = deploy.getProxyInfo();

		if (proxyInfo == null)
			return null;
		if (proxyInfo.getDefaultDomainYn() == null || !proxyInfo.getDefaultDomainYn().equals("N"))
			return null;
		if (StringUtils.isEmpty(proxyInfo.getTlsCrt()) || StringUtils.isEmpty(proxyInfo.getTlsKey())) {
			return null;
		}

		V1Secret secret = new V1Secret();
		secret.setApiVersion(K8S.Kind.Secret.getApiVersion());
		secret.setKind(K8S.Kind.Secret.name());

		// kbcard 네이밍기준 적용
		//String name = getTlsSecretName(deploy.getName());
		//secret.setMetadata(getMetadata(name, deploy.getNamespace()));

		secret.setType("kubernetes.io/tls");

		Map<String, byte[]> data = new HashMap<>();
		data.put("tls.crt", proxyInfo.getTlsCrt().trim().getBytes());
		data.put("tls.key", proxyInfo.getTlsKey().trim().getBytes());
		secret.setData(data);

//		byte[] crtBuf = Base64.encodeBase64(proxyInfo.getTlsCrt().trim().getBytes());
//		byte[] keyBuf = Base64.encodeBase64(proxyInfo.getTlsKey().trim().getBytes());

		deploy.getProxyInfo().setTlsSecretName(secret.getMetadata().getName());

		return secret;


	}

	private String getServiceName(K8SDeployDTO deploy) {

		String name = getName(K8S.Kind.Service.getPostfix(), deploy.getName());
		if (deploy.getPorts() != null && !deploy.getPorts().isEmpty()) {
			name = String.format("%s-%s", name, deploy.getPorts().get(0).getPort());
		}

		return name;
	}

	// Service
	private V1Service getService(K8SDeployDTO deploy) {

		if (deploy.getPorts() == null || deploy.getPorts().isEmpty()) {
			return null;
		}

		V1Service service = new V1Service();
		service.setApiVersion(K8S.Kind.Service.getApiVersion());
		service.setKind(K8S.Kind.Service.name());

		service.setMetadata(getMetadata(getServiceName(deploy), deploy.getNamespace()));

		// spec
		V1ServiceSpec spec = new V1ServiceSpec();
		service.setSpec(spec);

		// spec.selector
		// spec.setSelector(deploy.getLabels());
		spec.setSelector(new HashMap<String, String>(deploy.getLabels()));

		// spec.port
		for (K8SPort port : deploy.getPorts()) {
			V1ServicePort servicePort = new V1ServicePort();
			servicePort.setName(port.getName());
			servicePort.setPort(port.getPort());
			servicePort.setTargetPort(new IntOrString(port.getContainerPort()));
			servicePort.setNodePort(port.getNodePort());
			spec.addPortsItem(servicePort);
		}

		// spec.type
		if ("Headless".equals(deploy.getServiceType())) {
			spec.setType("ClusterIP");
			spec.clusterIP("None");
		} else {
			spec.setType(deploy.getServiceType());
		}

		// spec.externalIPs
		if (!isEmpty(deploy.getExternalIPs())) {
			spec.setLoadBalancerIP(deploy.getExternalIPs().get(0));
			//spec.setExternalIPs(deploy.getExternalIPs());
		}

		return service;

	}

	// Rollout용 preview service
	// only ClusterIP
	private V1Service getPreviewService(K8SDeployDTO deploy) {

		if (deploy.getPorts() == null || deploy.getPorts().isEmpty()) {
			return null;
		}

		V1Service service = new V1Service();
		service.setApiVersion(K8S.Kind.Service.getApiVersion());
		service.setKind(K8S.Kind.Service.name());

		String previewServiceName = String.format("%s-preview", getServiceName(deploy));
		service.setMetadata(getMetadata(previewServiceName, deploy.getNamespace()));

		// spec
		V1ServiceSpec spec = new V1ServiceSpec();
		service.setSpec(spec);

		// spec.selector
		// spec.setSelector(deploy.getLabels());
		spec.setSelector(new HashMap<String, String>(deploy.getLabels()));

		// spec.port
		for (K8SPort port : deploy.getPorts()) {
			V1ServicePort servicePort = new V1ServicePort();
			servicePort.setName(port.getName());
			servicePort.setPort(port.getPort());
			servicePort.setTargetPort(new IntOrString(port.getContainerPort()));
			spec.addPortsItem(servicePort);
		}

		// spec.setType(V1ServiceSpec.SERIALIZED_NAME_CLUSTER_I_P);
		spec.setType("ClusterIP");
		return service;

	}


	private V2beta2HorizontalPodAutoscaler getAutoscaler(K8SDeployDTO deploy, KubernetesObject object) {

		K8SAutoscale autoscale = deploy.getAutoscale();
		if (autoscale == null)
			return null;
		if (autoscale.getMaxReplicas() < 1)
			return null;

		V2beta2HorizontalPodAutoscaler autoscaler = new V2beta2HorizontalPodAutoscaler();
		autoscaler.apiVersion(K8S.Kind.HorizontalPodAutoscaler.getApiVersion());
		autoscaler.setKind(K8S.Kind.HorizontalPodAutoscaler.name());
		String name = getName(K8S.Kind.HorizontalPodAutoscaler.getPostfix(), deploy.getName());

		// spce
		V2beta2HorizontalPodAutoscalerSpec spec = new V2beta2HorizontalPodAutoscalerSpec();
		autoscaler.setSpec(spec);

		spec.setMinReplicas(deploy.getReplicas());
		int maxReplica = (autoscale.getMaxReplicas() >= deploy.getReplicas()) ? autoscale.getMaxReplicas()
				: deploy.getReplicas();
		autoscale.setMaxReplicas(maxReplica);
		spec.setMaxReplicas(autoscale.getMaxReplicas());

		// targetRef
		V2beta2CrossVersionObjectReference targetRef = new V2beta2CrossVersionObjectReference();
		targetRef.setApiVersion(object.getApiVersion());
		targetRef.setKind(object.getKind());
		targetRef.setName(object.getMetadata().getName());
		spec.setScaleTargetRef(targetRef);

		// metrics
		List<V2beta2MetricSpec> metrics = new ArrayList<V2beta2MetricSpec>();
		spec.setMetrics(metrics);

		V2beta2MetricSpec metric = new V2beta2MetricSpec();
		metric.setType("Resource");

		V2beta2ResourceMetricSource resource = new V2beta2ResourceMetricSource();
		resource.setName(autoscale.getResourceName()); // resourceName
		V2beta2MetricTarget target = new V2beta2MetricTarget();
		target.type("Utilization");
		target.averageUtilization(autoscale.getAverageUtilization()); // average
		resource.setTarget(target);

		metric.setResource(resource);
		metrics.add(metric);

		// 네이밍룰적용
		name = String.format("%s-%s-%s-%s", name, spec.getMinReplicas(), spec.getMaxReplicas(),
				autoscale.getResourceName());
		autoscaler.setMetadata(getMetadata(name, deploy.getNamespace()));

		return autoscaler;
	}

	// VolumeName ex)mountPath-001
	private void setVolumeName(String deployName, List<K8SVolume> volumeList) {
		if (volumeList == null)
			return;

		String name = getName("vol", deployName);
		String claimName = getName("pvc", deployName);

		int index = 0;
		for (K8SVolume volume : volumeList) {
			String postName = volume.getMountPath().replaceAll("/", "");
			name = getName("vol", deployName);
			name = String.format("%s-%s-00%s", name, postName, ++index);
			claimName = getName("pvc", deployName);
			claimName = String.format("%s-%s-00%s", claimName, postName, index);
			volume.setName(name);
			volume.setClaimName(claimName);
		}
	}

	//private final String KBC_AccessMode = "ReadWriteMany";
	//private final String KBC_StorageClassName = "tkg-storage-powerstore1000t-nas-001";
	private final String KBC_VolumeMode = "Filesystem";

	private V1PersistentVolumeClaim getPvc(K8SDeployDTO deploy, K8SVolume volume) {

		//String name = getPvcName(deploy, volume);
		//volume.setClaimName(name);

		V1PersistentVolumeClaim pvc = new V1PersistentVolumeClaim();
		pvc.apiVersion(K8S.Kind.PersistentVolumeClaim.getApiVersion());
		pvc.setKind(K8S.Kind.PersistentVolumeClaim.name());
		pvc.setMetadata(getMetadata(volume.getClaimName(), deploy.getNamespace()));

		// spec
		V1PersistentVolumeClaimSpec spec = new V1PersistentVolumeClaimSpec();
		spec.addAccessModesItem(volume.getType());
		// spec.resource
		V1ResourceRequirements resource = new V1ResourceRequirements();
		resource.putRequestsItem("storage", new Quantity(volume.getRequestStorage()));
		spec.setResources(resource);
		// spec.storageClass
		spec.setStorageClassName(volume.getStorageClassName());
		spec.volumeMode(KBC_VolumeMode);
		pvc.setSpec(spec);

		return pvc;
	}



}
