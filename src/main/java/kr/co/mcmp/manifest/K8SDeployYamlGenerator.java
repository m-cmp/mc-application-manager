package kr.co.mcmp.manifest;

//import io.kubernetes.client.common.KubernetesObject;
//import io.kubernetes.client.custom.IntOrString;
//import io.kubernetes.client.custom.Quantity;
//import io.kubernetes.client.openapi.models.*;
//import io.kubernetes.client.util.Yaml;
//import kr.co.mcmp.devops.dto.k8s.*;
//import kr.co.mcmp.devops.model.httpproxy.*;
//import kr.co.mcmp.devops.model.rollout.BlueGreen;
//import kr.co.mcmp.devops.model.rollout.Canary;
//import kr.co.mcmp.devops.model.rollout.Pause;
//import kr.co.mcmp.devops.model.rollout.Rollout;
//import kr.co.mcmp.devops.model.rollout.RolloutSpec;
//import kr.co.mcmp.devops.model.rollout.RolloutStrategy;
//
//import org.apache.commons.lang.StringUtils;
//
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;

//@Component
public class K8SDeployYamlGenerator {
/*
	private static Logger logger = LoggerFactory.getLogger(K8SDeployYamlGenerator.class);

	public String generateDeployYaml(K8SDeployDTO deploy) {

		StringBuffer buffer = new StringBuffer();

		// defaudlt Label 추가
		Map<String, String> labels = addDefaultLabel(deploy.getLabels(), deploy.getDeployName());
		deploy.setLabels(labels);
		
		//pvc 네이밍룰 적용 (kbcard 기준)
		setVolumeName(deploy.getName(), deploy.getPvcVolumes());
		if (deploy.getPvcVolumes() != null) {
			for (K8SVolume volume : deploy.getPvcVolumes()) {
				appendYaml(buffer, getPvc(deploy, volume));
			}
		}

		switch (K8S.Controller.valueOf(deploy.getController())) {
		case Deployment:
			V1Deployment deployment = getDeployment(deploy);
			appendYaml(buffer, deployment);
			appendYaml(buffer, getAutoscaler(deploy, deployment));
			break;
		case DaemonSet:
			appendYaml(buffer, getDaemonSet(deploy));
			break;
		case StatefulSet:
			appendYaml(buffer, getStatefulSet(deploy));
			break;
		case CronJob:
			appendYaml(buffer, getCronJob(deploy));
			break;
		case Rollout:
			Rollout rollout = getRollout(deploy);
			appendYaml(buffer, rollout);
			if (BlueGreen.name.equals(deploy.getStrategyType())) {
				appendYaml(buffer, getPreviewService(deploy));
			}
			appendYaml(buffer, getAutoscaler(deploy, rollout));
			break;
		}

		// service
		appendYaml(buffer, getService(deploy));

		appendYaml(buffer, getConfigMap(deploy));
		appendYaml(buffer, getSecret(deploy));

		// httprpoxy
		appendYaml(buffer, getTlsSecret(deploy));
		appendYaml(buffer, getHTTPProxy(deploy));

		// ingress kbcard ingress 사용하지 않음
		// appendYaml(buffer, getIngress(deploy));

		String yaml = buffer.toString();
		logger.info("[generateYaml]{}", yaml);
		
		
		//default Label 삭제
		removeDefaultLabel(deploy.getLabels());
		
		return yaml;

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
	private V1Deployment getDeployment(K8SDeployDTO deploy) {

		V1Deployment deployment = new V1Deployment();
		deployment.setApiVersion(K8S.Controller.Deployment.getApiVersion());
		deployment.setKind(deploy.getController());

		// metadata
		V1ObjectMeta metadata = getControllerMetadata(deploy.getController(), deploy.getName(), deploy.getNamespace(),
				deploy.getLabels());
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
		deploymentStrategy.setType(deploy.getStrategyType());
		deploymentSpec.setStrategy(deploymentStrategy);

		// template(pod)
		deploymentSpec.setTemplate(getPodTemplateSpec(deploy));
		return deployment;
	}

	// Rollout
	private Rollout getRollout(K8SDeployDTO deploy) {

		Rollout rollout = new Rollout();
		rollout.setApiVersion(K8S.Controller.Rollout.getApiVersion());
		rollout.setKind(deploy.getController());

		// metadata
		V1ObjectMeta metadata = getControllerMetadata(deploy.getController(), deploy.getName(), deploy.getNamespace(),
				deploy.getLabels());
		rollout.setMetadata(metadata);

		// spec
		RolloutSpec rolloutSpec = new RolloutSpec();
		rollout.setSpec(rolloutSpec);

		// spec.selector
		V1LabelSelector labelSelector = new V1LabelSelector();
		labelSelector.setMatchLabels(deploy.getLabels());
		rolloutSpec.setSelector(labelSelector);

		// spec.replicas
		Integer replicas = (deploy.getReplicas() == null || deploy.getReplicas() == 0) ? 1 : deploy.getReplicas();
		rolloutSpec.setReplicas(replicas);

		// spec.strategy
		RolloutStrategy strategy = new RolloutStrategy();
		
		switch(deploy.getStrategyType()) {
		case BlueGreen.name:
			BlueGreen blueGreen = new BlueGreen();
			String activeServiceName = getServiceName(deploy);
			String previewServiceName = String.format("%s-preview", activeServiceName);
			blueGreen.setActiveService(activeServiceName);
			blueGreen.setPreviewService(previewServiceName);
			blueGreen.setAutoPromotionEnabled(true);
			// blueGreen.setAutoPromotionSeconds(replicas);
			strategy.setBlueGreen(blueGreen);
			break;
		case Canary.name:
			Canary canary = getCanary(deploy, false);
			strategy.setCanary(canary);
			break;
		}

		rolloutSpec.setStrategy(strategy);

		// template(pod)
		rolloutSpec.setTemplate(getPodTemplateSpec(deploy));
		return rollout;
	}
	
	
	private Canary getCanary(K8SDeployDTO deploy, boolean promote) {
		
		Canary canary = new Canary();
		Map<String, Object> stepMap = null;
		
		if (promote) {
			
			//canary.setMaxSurge("100%");
			canary.setMaxUnavailable(0);
			
			//50%
			stepMap = new HashMap<> (1);
			stepMap.put("setWeight", 50);
			canary.addStep(stepMap);
			
			//5sec
			stepMap = new HashMap<> (1);
			stepMap.put("pause", new Pause(5));
			canary.addStep(stepMap);
			
			//75%
			stepMap = new HashMap<> (1);
			stepMap.put("setWeight", 75);
			canary.addStep(stepMap);
			
			//5sec
			stepMap = new HashMap<> (1);
			stepMap.put("pause", new Pause(5));
			canary.addStep(stepMap);

			//100
			stepMap = new HashMap<> (1);
			stepMap.put("setWeight", 100);
			canary.addStep(stepMap);
			
		} else {
			
			//canary.setMaxSurge("25%");
			canary.setMaxUnavailable(0);
			
			//순서중요!! Object Model 만들어 Yaml dump시 알파벳 오더링 규칙에 따라 순서가 뒤바뀜
			//int setWeight = Math.floorDiv(100, deploy.getReplicas());
			stepMap = new HashMap<> (1);
			stepMap.put("setWeight", 25);
			canary.addStep(stepMap);
			
			//suspend
			stepMap = new HashMap<> (1);
			stepMap.put("pause", new Pause());
			canary.addStep(stepMap);
			
		}
		
		return canary;
	}

	// DaemonSet
	private V1DaemonSet getDaemonSet(K8SDeployDTO deploy) {

		V1DaemonSet daemonSet = new V1DaemonSet();
		daemonSet.setApiVersion(K8S.Controller.DaemonSet.getApiVersion());
		daemonSet.setKind(deploy.getController());

		// metadata
		V1ObjectMeta metadata = getControllerMetadata(deploy.getController(), deploy.getName(), deploy.getNamespace(),
				deploy.getLabels());
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
		daemonSetSpec.setTemplate(getPodTemplateSpec(deploy));
		return daemonSet;

	}

	// StatefulSet
	private V1StatefulSet getStatefulSet(K8SDeployDTO deploy) {

		V1StatefulSet statefulSet = new V1StatefulSet();
		statefulSet.setApiVersion(K8S.Controller.StatefulSet.getApiVersion());
		statefulSet.setKind(deploy.getController());

		// metadata
		V1ObjectMeta metadata = getControllerMetadata(deploy.getController(), deploy.getName(), deploy.getNamespace(),
				deploy.getLabels());
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
		statefulSetSpec.template(getPodTemplateSpec(deploy));

		// TODO volumeClaimTemplates

		return statefulSet;
	}

	// CronJob
	private V1beta1CronJob getCronJob(K8SDeployDTO deploy) {

		V1beta1CronJob cronJob = new V1beta1CronJob();
		cronJob.setApiVersion(K8S.Controller.CronJob.getApiVersion());
		cronJob.setKind(deploy.getController());

		// metadata
		V1ObjectMeta metadata = getControllerMetadata(deploy.getController(), deploy.getName(), deploy.getNamespace(),
				deploy.getLabels());
		cronJob.setMetadata(metadata);

		// spec
		V1beta1CronJobSpec cronJobSpec = new V1beta1CronJobSpec();
		cronJob.setSpec(cronJobSpec);

		// spec.
		// cronJobSpec.setConcurrencyPolicy("Forbid"); //Allow, Forbid, Replace
		// spec.schedule
		cronJobSpec.schedule(deploy.getSchedule());

		// spec.jobTemplate
		V1beta1JobTemplateSpec jobTemplate = new V1beta1JobTemplateSpec();
		cronJobSpec.jobTemplate(jobTemplate);

		// spec.jobTemplate.spec
		V1JobSpec spec = new V1JobSpec();
		jobTemplate.setSpec(spec);

		// pod
		V1PodTemplateSpec template = getPodTemplateSpec(deploy);
		template.getSpec().setRestartPolicy("Never");
		spec.setTemplate(template);

		return cronJob;
	}

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

	private V1ObjectMeta getMetadata(String name, String namespace) {

		V1ObjectMeta metadata = new V1ObjectMeta();
		metadata.setName(name);
		metadata.setNamespace(namespace);
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
		if (labels != null) {
			labels.remove(K8S.podAntiAffinityKey);
		}
		
		return labels;
	}
	

	// PodTemplate
	private V1PodTemplateSpec getPodTemplateSpec(K8SDeployDTO deploy) {

		V1PodTemplateSpec podTemplateSpec = new V1PodTemplateSpec();
		// template.metadata
		V1ObjectMeta metadata = new V1ObjectMeta();
		metadata.setLabels(new HashMap<String, String>(deploy.getLabels()));
		podTemplateSpec.setMetadata(metadata);

		// template.spec
		V1PodSpec podSpec = new V1PodSpec();

		// add imagepullsecrets
		if (StringUtils.isNotEmpty(deploy.getImagePullSecret())) {
			List<V1LocalObjectReference> imagePullSecretsList = new ArrayList<V1LocalObjectReference>(1);
			V1LocalObjectReference v1LocalObjectReference = new V1LocalObjectReference();
			v1LocalObjectReference.setName(deploy.getImagePullSecret());
			imagePullSecretsList.add(v1LocalObjectReference);
			podSpec.setImagePullSecrets(imagePullSecretsList);
		}

		podTemplateSpec.setSpec(podSpec);

		// template.spec.container
		List<V1Container> containers = new ArrayList<V1Container>(1);
		podSpec.setContainers(containers);

		V1Container container = new V1Container();
		containers.add(container);

		// template.spec.container.name
		container.setName(getContainerName(deploy.getName()));
		// template.spec.container.image
		container.setImage("DEPLOY_IMAGE_NAME");
		// template.spec.container.command
		if (!isEmpty(deploy.getCommand())) {
			container.setCommand(deploy.getCommand());
		}
		// template.spec.container.args
		if (!isEmpty(deploy.getArgs())) {
			container.setArgs(deploy.getArgs());
		}

		// template.spec.container.envFrom.configMapRef
		if (deploy.getConfigMapData() != null && !deploy.getConfigMapData().isEmpty()) {
			V1ConfigMapEnvSource configMapEnvSource = new V1ConfigMapEnvSource();
			configMapEnvSource.setName(getConfigMapName(deploy.getName()));
			V1EnvFromSource fromSource = new V1EnvFromSource();
			fromSource.setConfigMapRef(configMapEnvSource);
			container.addEnvFromItem(fromSource);
		}

		// template.spec.container.envFrom.secretRef
		// https://kubernetes.io/ko/docs/tasks/inject-data-application/distribute-credentials-secure/#%EC%8B%9C%ED%81%AC%EB%A6%BF%EC%9D%98-%EB%AA%A8%EB%93%A0-%ED%82%A4-%EA%B0%92-%EC%8C%8D%EC%9D%84-%EC%BB%A8%ED%85%8C%EC%9D%B4%EB%84%88-%ED%99%98%EA%B2%BD-%EB%B3%80%EC%88%98%EB%A1%9C-%EA%B5%AC%EC%84%B1%ED%95%98%EA%B8%B0
		if (deploy.getSecretData() != null && !deploy.getSecretData().isEmpty()) {
			V1SecretEnvSource secretEnvSource = new V1SecretEnvSource();
			secretEnvSource.setName(getSecretName(deploy.getName()));
			V1EnvFromSource secretFromSource = new V1EnvFromSource();
			secretFromSource.setSecretRef(secretEnvSource);
			container.addEnvFromItem(secretFromSource);
		}

		// template.spec.container.ports
		if (deploy.getPorts() != null && deploy.getPorts().size() > 0) {
			List<V1ContainerPort> containerPorts = new ArrayList<V1ContainerPort>();

			for (K8SPort port : deploy.getPorts()) {
				V1ContainerPort containerPort = new V1ContainerPort();
				containerPort.setName(port.getName());
				containerPort.setContainerPort(port.getContainerPort());
				containerPorts.add(containerPort);
			}

			container.setPorts(containerPorts);
		}

		// template.spec.container.volumeMounts
		setVolumeMounts(container, deploy.getHostPathVolumes());
		setVolumeMounts(container, deploy.getPvcVolumes());
		setVolumeMounts(container, deploy.getAzureFileVolumes());

		// template.spec.container.resources
		setResource(container, deploy.getResource());

		// template.spec.container.probe//////////////////////////////////////////////////////////

		// V1HTTPGetAction httpGet = new V1HTTPGetAction();
		// readinessProbe.setHttpGet(httpGet);
		
		
		  int containerPort = deploy.getPorts().get(0).getContainerPort();
		  
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
		 
		
		// template.spec.container.probe//////////////////////////////////////////////////////////

		// TODO ConfigMap, Secret

		// template.spec.hostname
		if (!StringUtils.isEmpty(deploy.getHostname())) {
			podSpec.setHostname(deploy.getHostname());
		}

		// template.spec.nodeSelector
		Map<String, String> nodeSelector = getNodeSelector(deploy.getNodeSelector());
		if (nodeSelector != null && nodeSelector.size() > 0) {
			podSpec.setNodeSelector(nodeSelector);
		}

		// template.spec.volume
		setHostPathVolumes(podSpec, deploy.getHostPathVolumes());
		setPVCVolumes(podSpec, deploy.getPvcVolumes());
		setAzureFileVolumes(podSpec, deploy.getAzureFileVolumes());

		// template.spec.affinity
		switch (K8S.Controller.valueOf(deploy.getController())) {
		case Deployment:
		case StatefulSet:
		case Rollout:
			podSpec.setAffinity(getPodAntiAffinity(deploy));
			break;
		case DaemonSet:
		case CronJob:
			break;
		}

		return podTemplateSpec;

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

	// AzureFileVolumes
	private void setAzureFileVolumes(V1PodSpec podSpec, List<K8SVolume> volumesList) {

		if (volumesList == null || volumesList.size() == 0) {
			return;
		}

		for (K8SVolume k8sVolume : volumesList) {
			V1Volume volume = new V1Volume();
			volume.setName(k8sVolume.getName());

			V1AzureFileVolumeSource azureFileVolumeSource = new V1AzureFileVolumeSource();
			azureFileVolumeSource.setSecretName(k8sVolume.getSecretName());
			azureFileVolumeSource.setShareName(k8sVolume.getShareName());
			volume.setAzureFile(azureFileVolumeSource);

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

	// NodeSelector
	private Map<String, String> getNodeSelector(List<String> nodeSelectorList) {

		if (isEmpty(nodeSelectorList)) {
			return null;
		}

		Map<String, String> nodeSelectorMap = new HashMap<>();
		for (String nodeSelector : nodeSelectorList) {
			if (!nodeSelector.contains(":"))
				continue;
			String[] array = nodeSelector.split(":");
			nodeSelectorMap.put(array[0].trim(), array[1].trim());
		}

		return nodeSelectorMap;

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

		configMap.setMetadata(getMetadata(getConfigMapName(deploy.getName()), deploy.getNamespace()));

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

		secret.setMetadata(getMetadata(getSecretName(deploy.getName()), deploy.getNamespace()));

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
		String name = getTlsSecretName(deploy.getName());
		secret.setMetadata(getMetadata(name, deploy.getNamespace()));

		secret.setType("kubernetes.io/tls");

		Map<String, byte[]> data = new HashMap<>();
		data.put("tls.crt", proxyInfo.getTlsCrt().trim().getBytes());
		data.put("tls.key", proxyInfo.getTlsKey().trim().getBytes());
		secret.setData(data);

//		byte[] crtBuf = Base64.encodeBase64(proxyInfo.getTlsCrt().trim().getBytes());
//		byte[] keyBuf = Base64.encodeBase64(proxyInfo.getTlsKey().trim().getBytes());
//		logger.info("SECRET-CRT\n{}<---\n----------------------------------------------", proxyInfo.getTlsCrt());
//		logger.info("SECRET-CRT\n{}<---\n----------------------------------------------", new String(crtBuf));
//		logger.info("SECRET-KEY\n{}<---\n----------------------------------------------", proxyInfo.getTlsKey());
//		logger.info("SECRET-KEY\n{}<---\n----------------------------------------------", new String(keyBuf));

		deploy.getProxyInfo().setTlsSecretName(secret.getMetadata().getName());

		return secret;


	}


	// kbcard 네이밍기준 적용
	private String getContainerName(String deployName) {
		String name = getName("pod", deployName);
		return String.format("%s-001", name);
	}

	// kbcard 네이밍기준 적용
	private String getSecretName(String deployName) {
		String name = getName(K8S.Kind.Secret.getPostfix(), deployName);
		return String.format("%s-input-001", name);
	}

	// kbcard 네이밍기준 적용
	private String getTlsSecretName(String deployName) {
		String name = getName(K8S.Kind.Secret.getPostfix(), deployName);
		return String.format("%s-httptls-001", name);
	}

	// kbcard 네이밍기준 적용
	private String getConfigMapName(String deployName) {
		String name = getName(K8S.Kind.ConfigMap.getPostfix(), deployName);
		return String.format("%s-input-001", name);
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

	private final String KBC_HttpProxy_LoadBalancerPolicy = "Cookie";

	// HttpProxy
	private HTTPProxy getHTTPProxy(K8SDeployDTO deploy) {

		K8SProxyInfo proxyInfo = deploy.getProxyInfo();
		if (proxyInfo == null)
			return null;
		if (StringUtils.isBlank(proxyInfo.getDomainName()))
			return null;

		HTTPProxy httpProxy = new HTTPProxy();
		httpProxy.setApiVersion(K8S.Kind.HTTPProxy.getApiVersion());
		httpProxy.setKind(K8S.Kind.HTTPProxy.name());

		String name = getName(K8S.Kind.HTTPProxy.getPostfix(), deploy.getName());
		//String p = (proxyInfo.getTlsYn().equals("Y")) ? "443" : "80";
		//name = String.format("%s-%s", name, p);

		httpProxy.setMetadata(getMetadata(name, deploy.getNamespace()));

		// spec
		HTTPProxySpec spec = new HTTPProxySpec();
		httpProxy.setSpec(spec);

		// virtualhost
		Virtualhost virtualhost = new Virtualhost();
		spec.setVirtualhost(virtualhost);
		// doamin
		virtualhost.setFqdn(proxyInfo.getDomainName());
		// tls
		if (proxyInfo.getTlsYn().equals("Y")) {
			Tls tls = new Tls();
			tls.setSecretName(proxyInfo.getTlsSecretName());
			virtualhost.setTls(tls);
		}

		List<Route> routeList = new ArrayList<Route>();
		spec.setRoutes(routeList);

		Route route = new Route();
		routeList.add(route);

		// LoadBalancerPolicy Cookie
		LoadBalancerPolicy loadBalancerPolicy = new LoadBalancerPolicy();
		loadBalancerPolicy.setStrategy(KBC_HttpProxy_LoadBalancerPolicy);
		route.setLoadBalancerPolicy(loadBalancerPolicy);

		List<RouteService> list = new ArrayList<RouteService>();
		route.setServices(list);

		// Service
		List<K8SPort> portList = deploy.getPorts();
		for (K8SPort port : portList) {
			RouteService service = new RouteService();
			service.setName(getServiceName(deploy));
			service.setPort(port.getPort());
//			if (StringUtils.isNotBlank(port.getProtocol())) {
//				service.setProtocol(port.getProtocol());
//			}

			list.add(service);
		}

		return httpProxy;
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

//	private String getPvcName(K8SDeployDTO deploy, K8SVolume volume) {
//		String name = getName(K8S.Kind.PersistentVolumeClaim.getPostfix(), deploy.getName());
//		name = String.format("%s-%s", name, volume.getName());
//		return name;
//	}

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

	// Ingress
	/**
	 * private ExtensionsV1beta1Ingress getIngress(K8SDeployDTO deploy) {
	 * 
	 * if (deploy.getPorts() == null || deploy.getPorts().isEmpty()) { return null;
	 * }
	 * 
	 * List<ExtensionsV1beta1HTTPIngressPath> paths = new ArrayList<> (); for
	 * (K8SPort port : deploy.getPorts()) {
	 * 
	 * if (port.getIngressPath() == null || port.getIngressPath().isEmpty()) {
	 * continue; }
	 * 
	 * //spec.rule.paths.path ExtensionsV1beta1HTTPIngressPath path = new
	 * ExtensionsV1beta1HTTPIngressPath(); String ingressPath =
	 * (deploy.getIngressPathRewriteYn().equals("Y")) ? String.format("%s(/|$)(.*)",
	 * port.getIngressPath()) : port.getIngressPath(); path.setPath(ingressPath);
	 * //spec.rule.paths.backend ExtensionsV1beta1IngressBackend backend = new
	 * ExtensionsV1beta1IngressBackend();
	 * backend.setServiceName(getName(K8S.Kind.Service.getPostfix(),
	 * deploy.getName())); backend.setServicePort(new IntOrString(port.getPort()));
	 * path.setBackend(backend);
	 * 
	 * paths.add(path); }
	 * 
	 * if (paths.size() == 0) { return null; }
	 * 
	 * ExtensionsV1beta1Ingress ingress = new ExtensionsV1beta1Ingress();
	 * ingress.apiVersion(K8S.Kind.Ingress.getApiVersion());
	 * ingress.setKind(K8S.Kind.Ingress.name());
	 * 
	 * //metadata V1ObjectMeta metadata = getMetadata(K8S.Kind.Ingress.getPostfix(),
	 * deploy.getName()); metadata.setNamespace(deploy.getNamespace());
	 * //metadata.annotation if (deploy.getIngressPathRewriteYn().equals("Y")) {
	 * metadata.setAnnotations(K8S.getIngressRewriteAnnotaions()); }
	 * ingress.setMetadata(metadata);
	 * 
	 * //spec ExtensionsV1beta1IngressSpec ingressSpec = new
	 * ExtensionsV1beta1IngressSpec(); ingress.setSpec(ingressSpec);
	 * 
	 * //spec.rules List<ExtensionsV1beta1IngressRule> rules = new ArrayList<>(1);
	 * ExtensionsV1beta1IngressRule rule = new ExtensionsV1beta1IngressRule();
	 * rule.setHttp(new ExtensionsV1beta1HTTPIngressRuleValue());
	 * rule.getHttp().setPaths(paths); rules.add(rule); ingressSpec.setRules(rules);
	 * 
	 * return ingress; }
	 **/

}
