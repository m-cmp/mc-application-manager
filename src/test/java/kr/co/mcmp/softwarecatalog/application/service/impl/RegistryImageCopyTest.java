package kr.co.mcmp.softwarecatalog.application.service.impl;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RegistryImageCopyTest {
    static final String DIGEST = "sha256:" + "1".repeat(64);
    private static final DockerRegistryAccess.Endpoint HTTP = new DockerRegistryAccess.Endpoint("nexus:5000", "nexus", 5000, false);
    class Harness extends RegistryImageCopy {
        Process process = mock(Process.class);
        List<String> command; Path directory;
        String output = "Copied", digest = DIGEST;
        boolean timeout, interrupted, unavailable;
        int exit;
        @Override Process start(ProcessBuilder builder) throws IOException {
            command = builder.command();
            Path auth = Path.of(command.get(command.indexOf("--dest-authfile") + 1)); directory = auth.getParent();
            assertThat(Files.getPosixFilePermissions(auth)).isEqualTo(PosixFilePermissions.fromString("rw-------"));
            assertThat(Files.getPosixFilePermissions(directory)).isEqualTo(PosixFilePermissions.fromString("rwx------"));
            assertThat(Files.readString(auth)).contains("auths", "nexus:5000").doesNotContain("test-secret");
            assertThat(command.toString()).doesNotContain("test-secret", "--dest-creds", "docker.sock", "docker-daemon:");
            if (unavailable) throw new IOException("Missing skopeo");
            if (digest != null) Files.writeString(Path.of(command.get(command.indexOf("--digestfile") + 1)), digest);
            when(process.getOutputStream()).thenReturn(new ByteArrayOutputStream());
            when(process.getInputStream()).thenReturn(new ByteArrayInputStream(output.getBytes(StandardCharsets.UTF_8)));
            when(process.inputReader(any())).thenReturn(new BufferedReader(new StringReader(output)));
            try {
                if (interrupted) when(process.waitFor(anyLong(), any(TimeUnit.class))).thenThrow(new InterruptedException());
                else when(process.waitFor(anyLong(), any(TimeUnit.class))).thenReturn(!timeout);
            } catch (InterruptedException impossible) { throw new AssertionError(impossible); }
            when(process.exitValue()).thenReturn(exit);
            when(process.isAlive()).thenReturn(timeout || interrupted);
            return process;
        }
        RegistryImageCopy.Result run() { return copy("busybox", "1.37.0", HTTP, "test-user", "test-secret"); }
        void cleaned() { assertThat(directory).doesNotExist(); }
    }
    @Test void copiesAllPlatformsWithTemporaryAuthAndDigestValidation() {
        var h = new Harness(); var result = h.run();
        assertThat(result.success()).isTrue(); assertThat(result.digest()).isEqualTo(DIGEST);
        assertThat(h.command).contains("skopeo", "--all", "--preserve-digests", "--src-tls-verify=true", "--dest-tls-verify=false",
                "docker://docker.io/library/busybox:1.37.0", "docker://nexus:5000/busybox:1.37.0");
        h.cleaned();
    }
    @Test void preservesNamespaceAndVerifiesHttpsDestination() {
        var h = new Harness();
        assertThat(h.copy("example/image", "v1", new DockerRegistryAccess.Endpoint("nexus:5000", "nexus", 5000, true),
                "test-user", "test-secret").success()).isTrue();
        assertThat(h.command).contains("docker://docker.io/example/image:v1", "--dest-tls-verify=true"); h.cleaned();
    }
    @Test void failedTransferRedactsCredentialsAndCleansUp() {
        var h = new Harness(); h.exit = 1; h.output = "unauthorized test-secret dGVzdC11c2VyOnRlc3Qtc2VjcmV0";
        var result = h.run(); assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("unauthorized").doesNotContain("test-secret", "dGVzdC11c2VyOnRlc3Qtc2VjcmV0"); h.cleaned();
    }
    @Test void timeoutTerminatesChildAndRemovesAuth() {
        var h = new Harness(); h.timeout = true;
        assertThat(h.run().error()).contains("timed out"); verify(h.process, atLeastOnce()).destroyForcibly(); h.cleaned();
    }
    @Test void interruptionRestoresInterruptFlagAndRemovesAuth() {
        var h = new Harness(); h.interrupted = true;
        try { assertThat(h.run().success()).isFalse(); assertThat(Thread.currentThread().isInterrupted()).isTrue(); h.cleaned(); }
        finally { Thread.interrupted(); }
    }
    @Test void unavailableToolCannotReportSuccess() {
        var h = new Harness(); h.unavailable = true;
        assertThat(h.run().success()).isFalse(); h.cleaned();
    }
    @Test void zeroExitWithoutValidDigestIsNotSuccess() {
        var h = new Harness(); h.digest = "not-a-digest";
        assertThat(h.run().success()).isFalse(); h.cleaned();
        var missing = new Harness(); missing.digest = null;
        assertThat(missing.run().success()).isFalse(); missing.cleaned();
    }
    @ParameterizedTest @ValueSource(strings={"../image", "Image", "registry:5000/image", "image;touch-file", "https://image", "", "image/"})
    void invalidNamesDoNotStartAProcess(String name) {
        var h = new Harness(); assertThat(h.copy(name, "latest", HTTP, "u", "p").success()).isFalse(); assertThat(h.command).isNull();
    }
    @ParameterizedTest @ValueSource(strings={"latest;evil", "bad tag", "", "sha256:123"})
    void invalidTagsDoNotStartAProcess(String tag) {
        var h = new Harness(); assertThat(h.copy("busybox", tag, HTTP, "u", "p").success()).isFalse(); assertThat(h.command).isNull();
    }
    @Test void missingCredentialsFailBeforeCreatingAuthFiles() {
        var h = new Harness(); assertThat(h.copy("busybox", "latest", HTTP, "", "").success()).isFalse(); assertThat(h.command).isNull();
    }
}
