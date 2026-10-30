package kr.co.mcmp.softwarecatalog.docker.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class VmDeploymentChecksTest {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(ints = {262144, 1048576})
    void sufficientValueNeedsNoSudoAndDoesNotTouchConfiguration(int value) throws Exception {
        Fixture fixture = fixture(value, false);
        Result result = run(fixture, preflight(fixture));
        assertThat(result.exit()).isZero();
        assertThat(result.output()).contains("unchanged");
        assertThat(Files.readString(fixture.current())).isEqualTo(value + "\n");
        assertThat(Files.exists(directory.resolve("sudo-called"))).isFalse();
        assertThat(Files.exists(fixture.configuration())).isFalse();
    }

    @Test void insufficientValueIsRaisedVerifiedAndPersisted() throws Exception {
        Fixture fixture = fixture(65530, true);
        Result result = run(fixture, preflight(fixture));
        assertThat(result.exit()).isZero();
        assertThat(result.output()).contains("previous=65530", "minimum=262144", "persisted=");
        assertThat(Files.readString(fixture.current())).isEqualTo("262144\n");
        assertThat(Files.readString(fixture.configuration()))
                .contains("# Managed by MC-Application Manager", "vm.max_map_count=262144");
    }

    @Test void permissionDenialIsActionableAndMakesNoChanges() throws Exception {
        Fixture fixture = fixture(65530, false);
        Result result = run(fixture, preflight(fixture));
        assertThat(result.exit()).isEqualTo(77);
        assertThat(result.output()).contains("vm.max_map_count=65530", "minimum=262144", "sudo");
        assertThat(Files.readString(fixture.current())).isEqualTo("65530\n");
        assertThat(Files.exists(fixture.configuration())).isFalse();
    }

    @Test void refusesToOverwriteAdministratorConfiguration() throws Exception {
        Fixture fixture = fixture(65530, true);
        Files.writeString(fixture.configuration(), "# administrator\nvm.max_map_count=1048576\n");
        Result result = run(fixture, preflight(fixture));
        assertThat(result.exit()).isNotZero();
        assertThat(result.output()).contains("Refusing to overwrite");
        assertThat(Files.readString(fixture.current())).isEqualTo("65530\n");
        assertThat(Files.readString(fixture.configuration())).contains("# administrator");
    }

    @Test void preservesAHigherPreviouslyManagedValue() throws Exception {
        Fixture fixture = fixture(65530, true);
        Files.writeString(fixture.configuration(), "# Managed by MC-Application Manager\nvm.max_map_count=1048576\n");
        assertThat(run(fixture, preflight(fixture)).exit()).isZero();
        assertThat(Files.readString(fixture.current())).isEqualTo("1048576\n");
    }

    @Test void kernelWriteFailureDoesNotPersistAFalseSuccess() throws Exception {
        Fixture fixture = fixture(65530, true);
        executable(fixture.bin().resolve("sysctl"), "#!/bin/sh\necho 'read-only kernel setting' >&2\nexit 1\n");
        Result result = run(fixture, preflight(fixture));
        assertThat(result.exit()).isNotZero();
        assertThat(result.output()).contains("kernel setting could not be changed");
        assertThat(Files.exists(fixture.configuration())).isFalse();
        try (var files = Files.list(fixture.configuration().getParent())) {
            assertThat(files.filter(p -> p.getFileName().toString().startsWith(".mcmp")).count()).isZero();
        }
    }

    @Test void reportsReadinessTimeoutInsteadOfAcceptingRunningButUnhealthy() throws Exception {
        Fixture fixture = fixture(262144, false);
        startupCommands(fixture, "running", "unhealthy", 0);
        Result result = run(fixture, VmDeploymentChecks.containerStartup("printf '" + "a".repeat(64) + "'", null, 12));
        assertThat(result.exit()).isEqualTo(124);
        assertThat(result.output()).contains("readiness timed out", "health=unhealthy");
    }

    @Test void waitsForStableRunningEvenWithoutAnImageHealthcheck() throws Exception {
        Fixture fixture = fixture(262144, false);
        startupCommands(fixture, "running", "none", 0);
        Result result = run(fixture, VmDeploymentChecks.containerStartup("printf '" + "a".repeat(64) + "'", null, 12));
        assertThat(result.exit()).isZero();
        assertThat(result.output()).contains("Container startup verified");
        assertThat(Integer.parseInt(Files.readString(directory.resolve("clock")).trim())).isGreaterThanOrEqualTo(6);
    }

    @Test void restartDuringStartupIsNotReportedAsSuccess() throws Exception {
        Fixture fixture = fixture(262144, false);
        startupCommands(fixture, "running", "none", 1);
        Result result = run(fixture, VmDeploymentChecks.containerStartup("printf '" + "a".repeat(64) + "'", null, 12));
        assertThat(result.exit()).isNotZero();
        assertThat(result.output()).contains("failed during startup", "restarts=1");
    }

    private String preflight(Fixture fixture) {
        return VmDeploymentChecks.elasticsearchPrerequisites()
                .replace("/proc/sys/vm/max_map_count", fixture.current().toString())
                .replace("/etc/sysctl.d", fixture.configuration().getParent().toString());
    }

    private Fixture fixture(int value, boolean sudoAllowed) throws Exception {
        Path bin = Files.createDirectories(directory.resolve("bin"));
        Path current = directory.resolve("current");
        Files.writeString(current, value + "\n");
        Path config = Files.createDirectories(directory.resolve("sysctl.d")).resolve("99-mcmp-elasticsearch.conf");
        executable(bin.resolve("sudo"), "#!/bin/sh\ntouch '" + directory.resolve("sudo-called") + "'\n"
                + (sudoAllowed ? "[ \"$1\" = -n ] && shift\nexec \"$@\"\n" : "exit 1\n"));
        executable(bin.resolve("sysctl"), "#!/bin/sh\nprintf '%s\\n' \"${2#*=}\" > '" + current + "'\n");
        return new Fixture(bin, current, config);
    }

    private void startupCommands(Fixture fixture, String state, String health, int restarts) throws Exception {
        Path clock = directory.resolve("clock");
        Files.writeString(clock, "0\n");
        executable(fixture.bin().resolve("date"), "#!/bin/sh\nn=$(cat '" + clock + "'); n=$((n+1)); echo $n > '" + clock + "'; echo $n\n");
        executable(fixture.bin().resolve("sleep"), "#!/bin/sh\nexit 0\n");
        executable(fixture.bin().resolve("docker"), "#!/bin/sh\ncase \"$*\" in\n"
                + "  *State.Status*) echo '" + state + "';;\n"
                + "  *State.Health*) echo '" + health + "';;\n"
                + "  *RestartCount*) echo '" + restarts + "';;\nesac\n");
    }

    private void executable(Path path, String content) throws Exception {
        Files.writeString(path, content);
        assertThat(path.toFile().setExecutable(true)).isTrue();
    }

    private Result run(Fixture fixture, String script) throws Exception {
        ProcessBuilder builder = new ProcessBuilder("sh", "-c", script).redirectErrorStream(true);
        builder.environment().put("PATH", fixture.bin() + ":" + System.getenv("PATH"));
        Process process = builder.start();
        assertThat(process.waitFor(Duration.ofSeconds(10).toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)).isTrue();
        return new Result(process.exitValue(), new String(process.getInputStream().readAllBytes()));
    }

    private record Fixture(Path bin, Path current, Path configuration) { }
    private record Result(int exit, String output) { }
}
