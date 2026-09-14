package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.io.ByteArrayInputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.security.Signature;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.fabric8.kubernetes.api.model.Secret;
import io.fabric8.kubernetes.client.KubernetesClient;
import org.bouncycastle.asn1.pkcs.PrivateKeyInfo;
import org.bouncycastle.openssl.PEMKeyPair;
import org.bouncycastle.openssl.PEMParser;
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentConfigDTO;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.dto.K8sIngressTlsSettings;

/** Read-only certificate selection. IBM, cert-manager or operators own issuance and renewal. */
final class IbmIngressTlsResolver {
    static final String BINDINGS_CONFIG_MAP = "am-ingress-tls-bindings";
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final java.security.Provider PEM_PROVIDER = new BouncyCastleProvider();
    private static final String DNS = "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+";

    private IbmIngressTlsResolver() {}

    static DeploymentConfigDTO resolve(KubernetesClient client, String namespace, DeploymentConfigDTO config) {
        if (!config.isIngressEnabled() || !IbmIngressSupport.managed(config.getIngressClass())) return config;
        // HTTP deliberately needs no certificate or DNS ownership. Do not inspect TLS resources.
        if (!config.isTlsEnabled()) return config.toBuilder().ingressTlsSecret(null).build();
        String host = config.getIngressHost();
        if (!validPattern(host) || host.startsWith("*.")) {
            throw new IllegalArgumentException("IBM managed HTTPS requires a specific DNS hostname, not a wildcard.");
        }
        String name = selectBinding(bindings(client, namespace), host);
        if (name == null) {
            String reference = defaultSecretReference(client, config.getIngressClass());
            String[] parts = reference.split("/", -1);
            if (!namespace.equals(parts[0])) {
                throw new IllegalArgumentException("IBM's default certificate is in another namespace. Register a managed TLS Secret in the application namespace using " + BINDINGS_CONFIG_MAP + ".");
            }
            name = parts[1];
            X509Certificate certificate = certificate(client, namespace, name);
            String domain = ibmDomain(certificate);
            if (!(host.equals(domain) || matches("*." + domain, host))) {
                throw new UnregisteredHostException("Use a hostname under " + domain
                        + ", or ask an administrator to register this custom domain in " + BINDINGS_CONFIG_MAP + ".");
            }
            verifyHost(certificate, host);
        } else {
            verifyHost(certificate(client, namespace, name), host);
        }
        // Ignore hidden/stale catalog or client TLS overrides: the administrator controls IBM bindings.
        return config.toBuilder().ingressTlsEnabled(true).ingressTlsSecret(name).build();
    }

    static void apply(DeploymentRequest request, DeploymentConfigDTO resolved) {
        if (!resolved.isIngressEnabled() || !IbmIngressSupport.managed(resolved.getIngressClass())) return;
        request.setIngressClass(resolved.getIngressClass());
        request.setIngressTlsEnabled(resolved.isTlsEnabled());
        request.setIngressTlsSecret(resolved.getIngressTlsSecret());
    }

    static K8sIngressTlsSettings describe(KubernetesClient client, String namespace, String clazz) {
        if (!IbmIngressSupport.managed(clazz)) return new K8sIngressTlsSettings(false, null, List.of(), List.of());
        var registered = bindings(client, namespace);
        try {
            String[] reference = defaultSecretReference(client, clazz).split("/", -1);
            if (!namespace.equals(reference[0])) throw new IllegalArgumentException("The IBM default TLS Secret must be available in the application's Kubernetes namespace.");
            String domain = ibmDomain(certificate(client, namespace, reference[1]));
            return new K8sIngressTlsSettings(true, domain, List.copyOf(registered.keySet()), List.of());
        } catch (IllegalArgumentException e) {
            // Custom bindings can still be used when the default certificate is unavailable.
            return new K8sIngressTlsSettings(true, null, List.copyOf(registered.keySet()), List.of(e.getMessage()));
        }
    }

    static Map<String, String> bindings(KubernetesClient client, String namespace) {
        io.fabric8.kubernetes.api.model.ConfigMap cm;
        try { cm = client.configMaps().inNamespace(namespace).withName(BINDINGS_CONFIG_MAP).get(); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Cannot read IBM TLS domain bindings. Check ConfigMap read permission and connectivity."); }
        if (cm == null) return Map.of();
        try {
            String content = cm.getData() == null ? null : cm.getData().get("bindings.json");
            if (content == null || content.isBlank() || content.length() > 65536) throw new IllegalArgumentException();
            var entries = JSON.readTree(content);
            if (!entries.isArray()) throw new IllegalArgumentException();
            Map<String, String> result = new LinkedHashMap<>();
            for (var entry : entries) {
                if (!entry.isObject() || entry.size() != 2 || !entry.path("host").isTextual() || !entry.path("secretName").isTextual()) throw new IllegalArgumentException();
                String host = entry.get("host").asText();
                String secret = entry.get("secretName").asText();
                if (!validPattern(host) || !validSecretName(secret) || result.putIfAbsent(host, secret) != null) throw new IllegalArgumentException();
            }
            return result;
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid " + BINDINGS_CONFIG_MAP + ": bindings.json must contain unique {host, secretName} entries with valid DNS names.");
        }
    }

    static String selectBinding(Map<String, String> entries, String host) {
        if (entries.containsKey(host)) return entries.get(host);
        var candidates = entries.entrySet().stream().filter(e -> matches(e.getKey(), host)).toList();
        if (candidates.size() > 1) throw new IllegalArgumentException("Ambiguous IBM TLS domain bindings.");
        return candidates.isEmpty() ? null : candidates.get(0).getValue();
    }

    private static String defaultSecretReference(KubernetesClient client, String clazz) {
        try {
            var ingressClass = client.network().v1().ingressClasses().withName(clazz).get();
            if (ingressClass == null || ingressClass.getSpec() == null || !("cloud.ibm.com/" + clazz).equals(ingressClass.getSpec().getController())) {
                throw new IllegalArgumentException("IBM managed Ingress class is unavailable.");
            }
            var deployments = client.apps().deployments().inNamespace("kube-system").withLabel("ingress-class", clazz).list().getItems();
            Set<String> references = new HashSet<>();
            for (var deployment : deployments) {
                Set<String> local = new HashSet<>();
                if (deployment.getSpec() != null && deployment.getSpec().getTemplate() != null && deployment.getSpec().getTemplate().getSpec() != null) {
                    for (var container : deployment.getSpec().getTemplate().getSpec().getContainers()) {
                        var args = container.getArgs() == null ? List.<String>of() : container.getArgs();
                        for (int i = 0; i < args.size(); i++) {
                            String arg = args.get(i);
                            if (arg.startsWith("--default-ssl-certificate=")) local.add(arg.substring("--default-ssl-certificate=".length()));
                            else if (arg.equals("--default-ssl-certificate") && i + 1 < args.size()) local.add(args.get(++i));
                        }
                    }
                }
                if (local.size() != 1) throw new IllegalArgumentException("Cannot identify the IBM default TLS Secret. Ask an administrator to check the managed Controller certificate configuration.");
                references.addAll(local);
            }
            if (references.size() != 1) throw new IllegalArgumentException("IBM managed Controllers must reference one unambiguous default TLS Secret.");
            String reference = references.iterator().next();
            String[] parts = reference.split("/", -1);
            if (parts.length != 2 || !validSecretName(parts[0]) || !validSecretName(parts[1])) throw new IllegalArgumentException("Invalid IBM default TLS Secret reference.");
            return reference;
        } catch (IllegalArgumentException e) { throw e; }
        catch (RuntimeException e) { throw new IllegalArgumentException("Cannot discover IBM's default certificate. Check IngressClass and Controller read permissions and connectivity."); }
    }

    static X509Certificate certificate(KubernetesClient client, String namespace, String name) {
        Secret secret;
        try { secret = client.secrets().inNamespace(namespace).withName(name).get(); }
        catch (RuntimeException e) { throw new IllegalArgumentException("Cannot read the selected IBM TLS Secret. Check Secret read permission and connectivity."); }
        if (secret == null) throw new IllegalArgumentException("Selected IBM TLS Secret '" + name + "' does not exist in namespace " + namespace + ".");
        try {
            var data = secret.getData();
            if (!"kubernetes.io/tls".equals(secret.getType()) || data == null || data.get("tls.crt") == null || data.get("tls.key") == null) throw new IllegalArgumentException();
            var cert = (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(data.get("tls.crt"))));
            cert.checkValidity();
            // Validate the key pair in memory, without logging or persisting secret contents.
            verifyKeyPair(cert, new String(Base64.getDecoder().decode(data.get("tls.key")), StandardCharsets.US_ASCII));
            return cert;
        } catch (Exception e) {
            throw new IllegalArgumentException("IBM TLS Secret '" + name + "' must contain a currently valid certificate and matching unencrypted private key. Ask the cluster administrator to repair it.");
        }
    }

    static void verifyKeyPair(X509Certificate cert, String pemKey) throws Exception {
        try (var pem = new PEMParser(new StringReader(pemKey))) {
            Object key = pem.readObject();
            var converter = new JcaPEMKeyConverter().setProvider(PEM_PROVIDER);
            // SEC1 EC private keys need not contain a public-key field. Use the certificate's public key.
            var privateKey = key instanceof PEMKeyPair pair ? converter.getPrivateKey(pair.getPrivateKeyInfo())
                    : key instanceof PrivateKeyInfo info ? converter.getPrivateKey(info) : null;
            if (privateKey == null) throw new IllegalArgumentException();
            String algorithm = switch (cert.getPublicKey().getAlgorithm()) {
                case "RSA" -> "SHA256withRSA";
                case "EC" -> "SHA256withECDSA";
                case "Ed25519", "EdDSA" -> "Ed25519";
                default -> throw new IllegalArgumentException();
            };
            var signature = Signature.getInstance(algorithm, PEM_PROVIDER);
            byte[] probe = "AM certificate key-pair check".getBytes(StandardCharsets.US_ASCII);
            signature.initSign(privateKey); signature.update(probe);
            byte[] signed = signature.sign();
            signature.initVerify(cert.getPublicKey()); signature.update(probe);
            if (!signature.verify(signed)) throw new IllegalArgumentException();
        }
    }

    private static String ibmDomain(X509Certificate certificate) {
        var domains = dnsNames(certificate).stream().filter(s -> s.startsWith("*.") && s.endsWith(".containers.appdomain.cloud"))
                .map(s -> s.substring(2)).distinct().toList();
        if (domains.size() != 1) throw new IllegalArgumentException("The Controller's default certificate does not identify one IBM Ingress domain. Register a custom domain binding instead.");
        return domains.get(0);
    }

    static void verifyHost(X509Certificate certificate, String host) {
        if (dnsNames(certificate).stream().noneMatch(pattern -> matches(pattern, host))) {
            throw new IllegalArgumentException("Selected IBM TLS certificate does not cover the Ingress Host. Use a covered hostname or ask an administrator to update the domain binding.");
        }
    }

    private static List<String> dnsNames(X509Certificate certificate) {
        try {
            var names = certificate.getSubjectAlternativeNames();
            if (names == null) return List.of();
            return names.stream().filter(n -> n.size() >= 2 && Integer.valueOf(2).equals(n.get(0)) && n.get(1) instanceof String)
                    .map(n -> ((String) n.get(1)).toLowerCase(Locale.ROOT)).toList();
        } catch (Exception e) { throw new IllegalArgumentException("Cannot read TLS certificate DNS names."); }
    }

    static boolean matches(String pattern, String host) {
        if (!validPattern(pattern) || !validPattern(host) || host.startsWith("*.")) return false;
        if (!pattern.startsWith("*.")) return pattern.equals(host);
        int dot = host.indexOf('.');
        return dot > 0 && host.substring(dot + 1).equals(pattern.substring(2));
    }

    private static boolean validPattern(String host) {
        if (host == null || host.length() > 253) return false;
        String dns = host.startsWith("*.") ? host.substring(2) : host;
        return dns.matches(DNS) && !dns.matches("[0-9.]+");
    }

    private static boolean validSecretName(String name) {
        return name != null && name.length() <= 253 && name.matches("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)*");
    }

    static final class UnregisteredHostException extends IllegalArgumentException {
        UnregisteredHostException(String message) { super(message); }
    }
}
