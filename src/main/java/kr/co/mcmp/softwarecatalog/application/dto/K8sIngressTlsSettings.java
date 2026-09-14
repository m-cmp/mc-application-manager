package kr.co.mcmp.softwarecatalog.application.dto;

import java.util.List;

/** Public routing hints only; never certificate contents or private keys. */
public record K8sIngressTlsSettings(boolean managedTls, String defaultDomain,
                                   List<String> customDomains, List<String> warnings) {}
