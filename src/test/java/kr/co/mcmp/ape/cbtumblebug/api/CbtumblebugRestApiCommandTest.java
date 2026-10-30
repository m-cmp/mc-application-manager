package kr.co.mcmp.ape.cbtumblebug.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;

import kr.co.mcmp.ape.cbtumblebug.dto.MciCommandResult;
import kr.co.mcmp.ape.cbtumblebug.exception.CbtumblebugException;
import kr.co.mcmp.softwarecatalog.docker.model.DockerCommandResult;
import kr.co.mcmp.softwarecatalog.docker.model.DockerTarget;
import kr.co.mcmp.softwarecatalog.docker.service.DockerCommandException;
import kr.co.mcmp.softwarecatalog.docker.service.DockerSshCommandExecutor;

@ExtendWith(MockitoExtension.class)
class CbtumblebugRestApiCommandTest {

    @Mock
    private CbtumblebugRestClient restClient;

    private CbtumblebugRestApi api;

    @BeforeEach
    void setUp() {
        api = new CbtumblebugRestApi(restClient);
        ReflectionTestUtils.setField(api, "cbtumblebugUrl", "mc-infra-manager");
        ReflectionTestUtils.setField(api, "cbtumblebugPort", "1323");
        ReflectionTestUtils.setField(api, "cbtumblebugId", "test-user");
        ReflectionTestUtils.setField(api, "cbtumblebugPass", "test-pass");
    }

    @Test
    void parsesObjectShapedStdoutAndStderr() {
        String response = """
                {
                  "results": [{
                    "stdout": {"0": "line-one\\nline-two\\n"},
                    "stderr": {"0": "warning\\n"}
                  }]
                }
                """;
        stubReadyAndCommand(response);

        MciCommandResult result =
                api.executeMciCommandResult("ns01", "mci01", "docker ps", null, "vm01");

        assertThat(result.stdout()).isEqualTo("line-one\nline-two\n");
        assertThat(result.stderr()).isEqualTo("warning\n");
    }

    @Test
    void joinsArrayOutputWithoutDiscardingEntries() {
        String response = """
                {
                  "results": [{
                    "stdout": ["first", "second"],
                    "stderr": []
                  }]
                }
                """;
        stubReadyAndCommand(response);

        MciCommandResult result =
                api.executeMciCommandResult("ns01", "mci01", "docker ps", null, "vm01");

        assertThat(result.stdout()).isEqualTo("first\nsecond");
        assertThat(result.stderr()).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"   \""})
    void preservesSuccessfulOutputWhenTransportErrorIsNullOrBlank(String errorJson) {
        stubReadyAndCommand("""
                {"results":[{"stdout":"ok\\n__MCMP_EXIT_CODE__=0\\n","stderr":"warning","error":%s}]}
                """.formatted(errorJson));

        MciCommandResult result =
                api.executeMciCommandResult("ns01", "mci01", "docker ps", null, "vm01");

        assertThat(result.stdout()).isEqualTo("ok\n__MCMP_EXIT_CODE__=0\n");
        assertThat(result.stderr()).isEqualTo("warning");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "dial tcp 192.0.2.10:22: i/o timeout",
            "ssh: handshake failed: unable to authenticate",
            "dial tcp 192.0.2.10:10022: connect: connection refused"
    })
    void preservesTransportFailureInsteadOfDiscardingItsCause(String remoteError) throws Exception {
        String errorJson = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(remoteError);
        stubReadyAndCommand("""
                {"results":[{"stdout":{},"stderr":{},"error":%s}]}
                """.formatted(errorJson));

        MciCommandResult result =
                api.executeMciCommandResult("ns01", "mci01", "docker ps", null, "vm01");

        assertThat(result.stdout()).isEmpty();
        assertThat(result.stderr()).contains("VM vm01", remoteError);
    }

    @Test
    void preservesNonzeroCompletionMarkerAndContainerIdForExistingFailureHandling() {
        stubReadyAndCommand("""
                {"results":[{"stdout":"__MCMP_CONTAINER_ID__=abc123\\n__MCMP_EXIT_CODE__=77\\n",
                "stderr":"Passwordless sudo is required","error":"Process exited with status 77"}]}
                """);

        DockerCommandResult result = new DockerSshCommandExecutor(api).executeAllowFailure(
                new DockerTarget("ns01", "mci01", "vm01"), "docker run nginx");

        assertThat(result.exitCode()).isEqualTo(77);
        assertThat(result.stdout()).isEqualTo("__MCMP_CONTAINER_ID__=abc123");
        assertThat(result.stderr()).contains("Passwordless sudo is required", "Process exited with status 77");
    }

    @Test
    void executorFailureIncludesTheTransportCauseWhenCompletionMarkerIsMissing() {
        stubReadyAndCommand("""
                {"results":[{"stdout":{},"stderr":{},
                "error":"dial tcp 192.0.2.10:22: i/o timeout"}]}
                """);

        assertThatThrownBy(() -> new DockerSshCommandExecutor(api).execute(
                new DockerTarget("ns01", "mci01", "vm01"), "docker ps"))
                .isInstanceOf(DockerCommandException.class)
                .hasMessageContaining("VM vm01")
                .hasMessageContaining("dial tcp 192.0.2.10:22: i/o timeout")
                .hasMessageNotContaining("no error output");
    }

    @Test
    void doesNotRepeatErrorAlreadyPresentInStderr() {
        stubReadyAndCommand("""
                {"results":[{"stdout":{},"stderr":"ssh session disconnected",
                "error":"ssh session disconnected"}]}
                """);

        MciCommandResult result =
                api.executeMciCommandResult("ns01", "mci01", "docker ps", null, "vm01");

        assertThat(result.stderr()).isEqualTo("ssh session disconnected");
    }

    @Test
    void redactsCredentialsFromTransportFailure() {
        stubReadyAndCommand("""
                {"results":[{"stdout":{},"stderr":{},
                "error":"ssh authentication failed: password=test-secret token=test-token"}]}
                """);

        MciCommandResult result =
                api.executeMciCommandResult("ns01", "mci01", "docker ps", null, "vm01");

        assertThat(result.stderr()).contains("VM vm01", "ssh authentication failed")
                .doesNotContain("test-secret", "test-token");
    }

    @Test
    void usesNodeIdToTargetExactlyOneVm() {
        String response = """
                {
                  "results": [{
                    "nodeId": "vm01",
                    "stdout": "ok",
                    "stderr": ""
                  }]
                }
                """;
        stubReadyAndCommand(response);

        api.executeMciCommandResult("ns01", "mci01", "docker ps", null, "vm01");

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restClient, atLeast(2)).request(urlCaptor.capture(), any(), any(), any(), any());
        String commandUrl = urlCaptor.getAllValues().stream()
                .filter(url -> url.contains("/cmd/infra/mci01"))
                .findFirst()
                .orElseThrow();

        assertThat(commandUrl).contains("nodeId=vm01").doesNotContain("vmId=");
    }

    @Test
    void refusesAmbiguousMultiVmResponse() {
        String response = """
                {
                  "results": [
                    {"stdout": "one", "stderr": ""},
                    {"stdout": "two", "stderr": ""}
                  ]
                }
                """;
        stubReadyAndCommand(response);

        assertThatThrownBy(() ->
                api.executeMciCommandResult("ns01", "mci01", "docker ps", null, "vm01"))
                .isInstanceOf(CbtumblebugException.class)
                .hasMessageContaining("Expected one VM command result");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void stubReadyAndCommand(String commandResponse) {
        when(restClient.request(anyString(), any(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String url = invocation.getArgument(0);
                    return url.endsWith("/readyz")
                            ? ResponseEntity.ok("ready")
                            : ResponseEntity.ok(commandResponse);
                });
    }
}
