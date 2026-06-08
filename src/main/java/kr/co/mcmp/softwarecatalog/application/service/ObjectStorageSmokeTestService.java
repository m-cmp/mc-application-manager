package kr.co.mcmp.softwarecatalog.application.service;

import java.net.URI;
import java.util.Locale;
import java.util.UUID;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.K8sClusterDto;
import kr.co.mcmp.softwarecatalog.CatalogRepository;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.dto.ObjectStorageSmokeTestRequest;
import kr.co.mcmp.softwarecatalog.application.dto.ObjectStorageSmokeTestResponse;
import kr.co.mcmp.softwarecatalog.application.dto.ObjectStorageSmokeTestResponse.CheckResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.client.config.ClientOverrideConfiguration;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@Service
@Slf4j
@RequiredArgsConstructor
public class ObjectStorageSmokeTestService {

    private static final String OBJECT_STORAGE_CAPABILITY = "object-storage";

    private final CatalogRepository catalogRepository;
    private final CbtumblebugRestApi cbtumblebugRestApi;

    public ObjectStorageSmokeTestResponse runSmokeTest(ObjectStorageSmokeTestRequest request) {
        ObjectStorageSmokeTestResponse response = ObjectStorageSmokeTestResponse.builder()
                .success(false)
                .backendType("s3")
                .build();

        SoftwareCatalog catalog = catalogRepository.findByIdWithCatalogRefs(request.getCatalogId())
                .orElseThrow(() -> new IllegalArgumentException("Catalog not found: " + request.getCatalogId()));

        if (!hasObjectStorageCapability(catalog)) {
            response.getChecks().add(fail("catalogCapability", "Catalog does not declare object-storage capability."));
            return response;
        }
        response.getChecks().add(ok("catalogCapability", "Catalog supports object-storage configuration."));

        K8sClusterDto clusterDto = cbtumblebugRestApi.getK8sClusterByName(request.getNamespace(), request.getClusterName());
        String detectedProvider = clusterDto != null && clusterDto.getConnectionConfig() != null
                ? clusterDto.getConnectionConfig().getProviderName()
                : "";
        response.setDetectedProvider(detectedProvider);
        response.getChecks().add(ok("targetCluster", "Target cluster provider detected."));

        ObjectStorageSmokeTestRequest.ObjectStorageConfig config = request.getObjectStorage();
        String validationError = validateConfig(config, detectedProvider);
        if (validationError != null) {
            response.getChecks().add(fail("inputValidation", validationError));
            return response;
        }
        response.getChecks().add(ok("inputValidation", "S3-compatible object storage fields are present."));

        String smokeKey = "am-smoke-check/" + UUID.randomUUID();
        String payload = "mc-application-manager object storage smoke check";

        try (S3Client s3Client = createS3Client(config)) {
            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(config.getBucket())
                            .key(smokeKey)
                            .build(),
                    RequestBody.fromString(payload));
            response.getChecks().add(ok("putObject", "Temporary object write succeeded."));

            ResponseBytes<GetObjectResponse> objectBytes = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder()
                            .bucket(config.getBucket())
                            .key(smokeKey)
                            .build());
            boolean readMatches = payload.equals(objectBytes.asUtf8String());
            response.getChecks().add(readMatches
                    ? ok("getObject", "Temporary object read succeeded.")
                    : fail("getObject", "Temporary object read returned unexpected content."));

            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(config.getBucket())
                    .key(smokeKey)
                    .build());
            response.getChecks().add(ok("deleteObject", "Temporary object cleanup succeeded."));

            response.setSuccess(response.getChecks().stream().allMatch(CheckResult::isSuccess));
            return response;
        } catch (S3Exception e) {
            log.warn("Object Storage smoke check failed with S3 error code: {}", 
                    e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : e.statusCode());
            response.getChecks().add(fail("s3Operation", "S3-compatible operation failed. Check endpoint, region, bucket, credentials, and path-style option."));
            return response;
        } catch (Exception e) {
            log.warn("Object Storage smoke check failed: {}", e.getClass().getSimpleName());
            response.getChecks().add(fail("s3Operation", "Object Storage smoke check failed before completion."));
            return response;
        }
    }

    private S3Client createS3Client(ObjectStorageSmokeTestRequest.ObjectStorageConfig config) {
        S3Configuration serviceConfiguration = S3Configuration.builder()
                .pathStyleAccessEnabled(Boolean.TRUE.equals(config.getForcePathStyle()))
                .build();

        S3ClientBuilder builder = S3Client.builder()
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.getAccessKey(), config.getSecretKey())))
                .region(Region.of(config.getRegion()))
                .serviceConfiguration(serviceConfiguration)
                .overrideConfiguration(ClientOverrideConfiguration.builder()
                        .apiCallTimeout(java.time.Duration.ofSeconds(20))
                        .apiCallAttemptTimeout(java.time.Duration.ofSeconds(10))
                        .build());

        if (StringUtils.isNotBlank(config.getEndpoint())) {
            builder.endpointOverride(URI.create(normalizeEndpoint(config.getEndpoint())));
        }

        return builder.build();
    }

    private String normalizeEndpoint(String endpoint) {
        String trimmed = endpoint.trim();
        if (trimmed.toLowerCase(Locale.ROOT).startsWith("http://")
                || trimmed.toLowerCase(Locale.ROOT).startsWith("https://")) {
            return trimmed;
        }
        return "https://" + trimmed;
    }

    private String validateConfig(ObjectStorageSmokeTestRequest.ObjectStorageConfig config, String detectedProvider) {
        if (config == null || !Boolean.TRUE.equals(config.getEnabled())) {
            return "Object Storage is not enabled.";
        }
        if (!"s3".equalsIgnoreCase(StringUtils.defaultIfBlank(config.getBackendType(), "s3"))
                && !"s3-compatible".equalsIgnoreCase(config.getBackendType())) {
            return "Only S3-compatible backend is supported in the current smoke check.";
        }
        if (StringUtils.isAnyBlank(config.getRegion(), config.getBucket(), config.getAccessKey(), config.getSecretKey())) {
            return "Region, bucket, access key, and secret key are required.";
        }
        if (!isAwsProvider(detectedProvider) && StringUtils.isBlank(config.getEndpoint())) {
            return "Endpoint is required for non-AWS S3-compatible object storage.";
        }
        return null;
    }

    private boolean isAwsProvider(String provider) {
        return StringUtils.containsIgnoreCase(provider, "aws");
    }

    private boolean hasObjectStorageCapability(SoftwareCatalog catalog) {
        if (catalog.getCatalogRefs() == null) {
            return false;
        }
        return catalog.getCatalogRefs().stream().anyMatch(ref ->
                OBJECT_STORAGE_CAPABILITY.equalsIgnoreCase(ref.getRefValue())
                        && ("CAPABILITY".equalsIgnoreCase(ref.getRefType()) || "TAG".equalsIgnoreCase(ref.getRefType())));
    }

    private CheckResult ok(String name, String message) {
        return CheckResult.builder().name(name).success(true).message(message).build();
    }

    private CheckResult fail(String name, String message) {
        return CheckResult.builder().name(name).success(false).message(message).build();
    }
}
