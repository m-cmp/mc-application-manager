package kr.co.mcmp.softwarecatalog.application.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Copies Registry images without a Docker daemon or host socket. */
@Component
public class RegistryImageCopy {
    @Value("${docker.push.timeout-seconds:600}")
    private long timeoutSeconds = 600;
    @Value("${docker.push.max-retries:3}")
    private int attempts = 3;

    record Result(boolean success, String digest, String error) {}

    Result copy(String imageName, String tag, DockerRegistryAccess.Endpoint destination,
                String username, String password) {
        if (imageName == null || imageName.length() > 255
                || !imageName.matches("[a-z0-9]+(?:[._-]+[a-z0-9]+)*(?:/[a-z0-9]+(?:[._-]+[a-z0-9]+)*)*")
                || tag == null || !tag.matches("[A-Za-z0-9_][A-Za-z0-9_.-]{0,127}"))
            return new Result(false, null, "Invalid Docker Hub image name or tag.");
        if (username == null || username.isBlank() || password == null || password.isEmpty())
            return new Result(false, null, "Nexus Registry credentials are missing.");

        Path directory = null;
        Process process = null;
        String auth = Base64.getEncoder().encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
        try {
            directory = Files.createTempDirectory("am-registry-copy-", PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rwx------")));
            Path credentials = Files.createFile(directory.resolve("auth.json"), PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rw-------")));
            Files.writeString(credentials, new ObjectMapper().writeValueAsString(Map.of("auths",
                    Map.of(destination.authority(), Map.of("auth", auth)))), StandardCharsets.UTF_8);
            Path digestFile = directory.resolve("digest");
            String source = "docker://docker.io/" + (imageName.contains("/") ? imageName : "library/" + imageName) + ":" + tag;
            String target = "docker://" + destination.authority() + "/" + imageName + ":" + tag;
            // Preserve the original multi-platform manifest and digests. No image is run or unpacked.
            List<String> command = List.of("skopeo", "copy", "--all", "--preserve-digests",
                    "--src-tls-verify=true", "--src-no-creds", "--dest-tls-verify=" + destination.secure(),
                    "--dest-authfile", credentials.toString(), "--digestfile", digestFile.toString(),
                    "--retry-times=" + Math.max(0, Math.min(10, attempts - 1)), source, target);
            process = start(new ProcessBuilder(command).redirectErrorStream(true));
            process.getOutputStream().close();
            Process running = process;
            var output = CompletableFuture.supplyAsync(() -> readOutput(running));
            if (!process.waitFor(Math.max(1, timeoutSeconds), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                return new Result(false, null, "Registry image copy timed out.");
            }
            String message = output.get(3, TimeUnit.SECONDS).replace(password, "[redacted]").replace(auth, "[redacted]");
            if (process.exitValue() != 0)
                return new Result(false, null, message.isBlank() ? "Registry image copy failed." : message);
            String digest = Files.readString(digestFile, StandardCharsets.UTF_8).trim();
            if (!digest.matches("sha256:[a-f0-9]{64}"))
                return new Result(false, null, "Registry image copy did not return a valid digest.");
            return new Result(true, digest, null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Result(false, null, "Registry image copy was interrupted.");
        } catch (Exception e) {
            // Do not expose process arguments, credentials or temporary auth-file contents.
            return new Result(false, null, "Registry image copy could not complete (" + e.getClass().getSimpleName()
                    + "). Verify that the AM image includes skopeo and that the Registry is reachable.");
        } finally {
            if (process != null && process.isAlive()) process.destroyForcibly();
            if (directory != null) {
                for (String name : List.of("auth.json", "digest")) {
                    try { Files.deleteIfExists(directory.resolve(name)); } catch (IOException ignored) { }
                }
                try { Files.deleteIfExists(directory); } catch (IOException ignored) { }
            }
        }
    }

    Process start(ProcessBuilder builder) throws IOException { return builder.start(); }

    private static String readOutput(Process process) {
        StringBuilder output = new StringBuilder();
        try (var reader = process.inputReader(StandardCharsets.UTF_8)) {
            char[] buffer = new char[2048];
            int count;
            while ((count = reader.read(buffer)) >= 0) {
                int remaining = 8192 - output.length();
                if (remaining > 0) output.append(buffer, 0, Math.min(count, remaining));
            }
        } catch (IOException ignored) { }
        return output.toString();
    }
}
