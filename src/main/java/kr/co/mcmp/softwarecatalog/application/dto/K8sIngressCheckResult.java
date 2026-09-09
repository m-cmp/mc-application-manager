package kr.co.mcmp.softwarecatalog.application.dto;

import java.util.List;

/** Blocking route/input errors are separate from non-blocking TLS readiness warnings. */
public record K8sIngressCheckResult(boolean valid, List<String> errors, List<String> warnings) {}
