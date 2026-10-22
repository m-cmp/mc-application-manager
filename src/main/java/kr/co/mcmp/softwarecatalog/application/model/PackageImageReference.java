package kr.co.mcmp.softwarecatalog.application.model;

import java.util.regex.Pattern;
import kr.co.mcmp.softwarecatalog.application.constants.ArtifactSource;
import kr.co.mcmp.softwarecatalog.application.dto.PackageInfoDTO;

/** Resolves the artifact selected for this package without changing seeded catalog sources. */
public final class PackageImageReference {
    private static final Pattern REFERENCE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/:@-]{0,254}");

    private PackageImageReference() {}

    public static String resolve(PackageInfoDTO info) {
        if (info == null) throw new IllegalArgumentException("A Docker package is required");
        return resolve(info.getArtifactSource(), info.getRepositoryUrl(), info.getPackageName(), info.getPackageVersion());
    }

    public static String resolve(PackageInfo info) {
        if (info == null) throw new IllegalArgumentException("A Docker package is required");
        return resolve(info.getArtifactSource(), info.getRepositoryUrl(), info.getPackageName(), info.getPackageVersion());
    }

    private static String resolve(ArtifactSource source, String stored, String name, String version) {
        if (source == ArtifactSource.NEXUS) return requireNexusReference(stored);
        if (name == null || name.isBlank() || version == null || version.isBlank())
            throw new IllegalArgumentException("Docker package name and version are required");
        String first = name.split("/", 2)[0];
        String reference = (first.contains(".") || first.contains(":") || "localhost".equals(first)
                ? name : "docker.io/" + name) + ":" + version;
        if (!REFERENCE.matcher(reference).matches()) throw new IllegalArgumentException("Invalid Docker image reference");
        return reference;
    }

    public static String requireNexusReference(String reference) {
        if (reference == null || !REFERENCE.matcher(reference).matches() || !reference.contains("/")
                || reference.contains("://"))
            throw new IllegalArgumentException("The imported package has no valid Nexus image reference");
        String registry = reference.substring(0, reference.indexOf('/'));
        String image = reference.substring(reference.indexOf('/') + 1);
        java.net.URI endpoint = java.net.URI.create("https://" + registry);
        if (endpoint.getHost() == null || endpoint.getUserInfo() != null || image.startsWith("/"))
            throw new IllegalArgumentException("Invalid Nexus Registry address");
        if (!(registry.contains(".") || registry.contains(":") || "localhost".equals(registry))
                || !(image.contains(":") || image.contains("@sha256:")))
            throw new IllegalArgumentException("The imported package must reference a versioned Nexus image");
        return reference;
    }
}
