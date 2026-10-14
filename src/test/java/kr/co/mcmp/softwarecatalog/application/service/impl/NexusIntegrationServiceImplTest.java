package kr.co.mcmp.softwarecatalog.application.service.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import kr.co.mcmp.oss.dto.OssDto;
import kr.co.mcmp.oss.service.OssService;
import kr.co.mcmp.service.oss.repository.CommonModuleRepositoryService;
import kr.co.mcmp.softwarecatalog.application.config.NexusConfig;
import kr.co.mcmp.util.Base64Utils;

@ExtendWith(MockitoExtension.class)
class NexusIntegrationServiceImplTest {

    @Mock
    private CommonModuleRepositoryService moduleRepositoryService;

    @Mock
    private NexusConfig nexusConfig;

    @Mock
    private OssService ossService;

    @Mock
    private RestTemplate restTemplate;

    private NexusIntegrationServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NexusIntegrationServiceImpl(
                moduleRepositoryService,
                nexusConfig,
                ossService,
                restTemplate);

        org.mockito.Mockito.lenient().when(ossService.getAllOssList()).thenReturn(List.of(
                OssDto.builder()
                        .ossName("NEXUS")
                        .ossUrl("http://nexus.test:8081/")
                        .ossUsername("admin")
                        .ossPassword(Base64Utils.base64Encoding("secret"))
                        .build()));
    }

    @Test
    void returnsTrueOnlyForExactChartVersionAndTgzAsset() {
        String response = """
                {
                  "items": [{
                    "repository": "helm-hosted",
                    "format": "helm",
                    "name": "jenkins",
                    "version": "5.8.56",
                    "assets": [{"path": "charts/jenkins-5.8.56.tgz"}]
                  }],
                  "continuationToken": null
                }
                """;
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(response));

        boolean exists = service.checkHelmChartExistsInNexus("helm-hosted", "jenkins", "5.8.56");

        assertThat(exists).isTrue();

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        @SuppressWarnings("rawtypes")
        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).exchange(
                uriCaptor.capture(),
                eq(HttpMethod.GET),
                entityCaptor.capture(),
                eq(String.class));

        var queryParams = UriComponentsBuilder.fromUri(uriCaptor.getValue()).build().getQueryParams();
        assertThat(uriCaptor.getValue().getPath()).isEqualTo("/service/rest/v1/search");
        assertThat(queryParams.getFirst("repository")).isEqualTo("helm-hosted");
        assertThat(queryParams.getFirst("name")).isEqualTo("jenkins");
        assertThat(queryParams.getFirst("version")).isEqualTo("5.8.56");
        assertThat(entityCaptor.getValue().getHeaders().getFirst(HttpHeaders.AUTHORIZATION))
                .isEqualTo("Basic " + Base64Utils.base64Encoding("admin:secret"));
    }

    @Test
    void rejectsPartialMatchesAndMetadataWithoutTheRequestedAsset() {
        String response = """
                {
                  "items": [
                    {"repository":"other-repo","format":"helm","name":"jenkins","version":"5.8.56","assets":[{"path":"jenkins-5.8.56.tgz"}]},
                    {"repository":"helm-hosted","format":"docker","name":"jenkins","version":"5.8.56","assets":[{"path":"jenkins-5.8.56.tgz"}]},
                    {"repository":"helm-hosted","format":"helm","name":"jenkins-operator","version":"5.8.56","assets":[{"path":"jenkins-operator-5.8.56.tgz"}]},
                    {"repository":"helm-hosted","format":"helm","name":"jenkins","version":"5.8.55","assets":[{"path":"jenkins-5.8.55.tgz"}]},
                    {"repository":"helm-hosted","format":"helm","name":"jenkins","version":"5.8.56","assets":[{"path":"jenkins-5.8.55.tgz"}]}
                  ],
                  "continuationToken": null
                }
                """;
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(response));

        assertThat(service.checkHelmChartExistsInNexus("helm-hosted", "jenkins", "5.8.56"))
                .isFalse();
    }

    @Test
    void searchesSubsequentPagesUsingTheContinuationToken() {
        String firstPage = """
                {
                  "items": [],
                  "continuationToken": "next page/+="
                }
                """;
        String secondPage = """
                {
                  "items": [{
                    "repository": "helm-hosted",
                    "format": "helm",
                    "name": "jenkins",
                    "version": "5.8.56",
                    "assets": [{"path": "jenkins-5.8.56.tgz"}]
                  }],
                  "continuationToken": null
                }
                """;
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(firstPage), ResponseEntity.ok(secondPage));

        assertThat(service.checkHelmChartExistsInNexus("helm-hosted", "jenkins", "5.8.56"))
                .isTrue();

        ArgumentCaptor<URI> uriCaptor = ArgumentCaptor.forClass(URI.class);
        verify(restTemplate, org.mockito.Mockito.times(2)).exchange(
                uriCaptor.capture(),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class));
        assertThat(uriCaptor.getAllValues().get(1).getRawQuery())
                .contains("continuationToken=next%20page%2F%2B%3D");
    }

    @ParameterizedTest
    @MethodSource("invalidSearchResponses")
    void treatsNexusErrorsAndInvalidResponsesAsNotFound(ResponseEntity<String> response) {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(response);

        assertThat(service.checkHelmChartExistsInNexus("helm-hosted", "jenkins", "5.8.56"))
                .isFalse();
    }

    private static Stream<Arguments> invalidSearchResponses() {
        return Stream.of(
                Arguments.of(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("failure")),
                Arguments.of(ResponseEntity.ok("")),
                Arguments.of(ResponseEntity.ok("not-json")),
                Arguments.of(ResponseEntity.ok("{\"continuationToken\":null}")),
                Arguments.of(ResponseEntity.ok("{\"items\":[],\"continuationToken\":null}")));
    }

    @Test
    void treatsNexusConnectionFailureAsNotFound() {
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenThrow(new ResourceAccessException("connection refused"));

        assertThat(service.checkHelmChartExistsInNexus("helm-hosted", "jenkins", "5.8.56"))
                .isFalse();
    }

    @ParameterizedTest
    @MethodSource("blankLookupValues")
    void rejectsBlankLookupValuesWithoutCallingNexus(String repository, String chart, String version) {
        assertThat(service.checkHelmChartExistsInNexus(repository, chart, version)).isFalse();

        verify(ossService, never()).getAllOssList();
        verify(restTemplate, never()).exchange(
                any(URI.class),
                eq(HttpMethod.GET),
                any(HttpEntity.class),
                eq(String.class));
    }

    @Test
    void imageCopyUsesInternalRegistryAndDoesNotRequireDockerEngine() {
        var copy = org.mockito.Mockito.mock(RegistryImageCopy.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "registryImageCopy", copy);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "dockerRegistryPort", 5000);
        org.springframework.test.util.ReflectionTestUtils.setField(service, "checkDockerDaemon", true);
        when(copy.copy(eq("busybox"), eq("1.37.0"), any(), eq("admin"), eq("secret")))
                .thenReturn(new RegistryImageCopy.Result(true, "sha256:" + "a".repeat(64), null));
        when(restTemplate.exchange(org.mockito.ArgumentMatchers.anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("[{\"name\":\"docker-hosted\",\"format\":\"docker\",\"type\":\"hosted\"}]"));
        var result = service.pushImageToNexus("busybox", "1.37.0", null);
        assertThat(result.get("success")).isEqualTo(true);
        assertThat(result.get("fullImageUrl")).isEqualTo("nexus.test:5000/busybox:1.37.0");
        assertThat(result.get("imageDigest")).isEqualTo("sha256:" + "a".repeat(64));
        verify(copy).copy(eq("busybox"), eq("1.37.0"), any(), eq("admin"), eq("secret"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings={"1.37.0", "other-version"})
    void dockerLookupRequiresTheExactImageAndTag(String version) {
        when(restTemplate.exchange(org.mockito.ArgumentMatchers.anyString(), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok("[{\"name\":\"docker-hosted\",\"format\":\"docker\",\"type\":\"hosted\"}]"));
        String body = "{\"items\":[{\"repository\":\"docker-hosted\",\"format\":\"docker\",\"name\":\"busybox\","
                + "\"version\":\"" + version + "\",\"assets\":[{\"path\":\"v2/busybox/manifests/" + version + "\"}]}]}";
        when(restTemplate.exchange(any(URI.class), eq(HttpMethod.GET), any(HttpEntity.class), eq(String.class)))
                .thenReturn(ResponseEntity.ok(body));
        assertThat(service.checkImageExistsInNexus("busybox", "1.37.0")).isEqualTo(version.equals("1.37.0"));
    }

    private static Stream<Arguments> blankLookupValues() {
        return Stream.of(
                Arguments.of(null, "jenkins", "5.8.56"),
                Arguments.of(" ", "jenkins", "5.8.56"),
                Arguments.of("helm-hosted", null, "5.8.56"),
                Arguments.of("helm-hosted", " ", "5.8.56"),
                Arguments.of("helm-hosted", "jenkins", null),
                Arguments.of("helm-hosted", "jenkins", " "));
    }
}
