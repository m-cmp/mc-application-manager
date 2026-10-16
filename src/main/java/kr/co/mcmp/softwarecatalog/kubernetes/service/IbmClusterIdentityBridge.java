package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

/** Runs only through Kubernetes exec in AM's temporary Pod. Never run as the Pod entrypoint. */
public final class IbmClusterIdentityBridge {
    private IbmClusterIdentityBridge() { }

    static byte[] field(String value, int id) throws IOException {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > 120) throw new IOException();
        var out = new ByteArrayOutputStream();
        out.write((id << 3) | 2); out.write(bytes.length); out.write(bytes);
        return out.toByteArray();
    }

    static byte[] rpc(String method, byte[] payload) throws Exception {
        byte[] frame = ByteBuffer.allocate(payload.length + 5).put((byte) 0).putInt(payload.length).put(payload).array();
        var process = new ProcessBuilder("curl", "--silent", "--show-error", "--fail", "--max-time", "70",
                "--noproxy", "*", "--unix-socket", "/sidecardir/provider.sock", "--http2-prior-knowledge",
                "--dump-header", "/dev/stderr", "-H", "content-type: application/grpc", "-H", "te: trailers",
                "--data-binary", "@-", "http://localhost/secretprovider.SecretProvider/" + method).start();
        try {
            try (var input = process.getOutputStream()) { input.write(frame); }
            byte[] output = process.getInputStream().readNBytes(65537);
            byte[] error = process.getErrorStream().readNBytes(16385);
            if (!process.waitFor(75, TimeUnit.SECONDS)) throw new IOException();
            int grpc = -1;
            var match = Pattern.compile("(?im)^grpc-status:\\s*([0-9]+)").matcher(new String(error, StandardCharsets.UTF_8));
            while (match.find()) grpc = Integer.parseInt(match.group(1));
            if (process.exitValue() != 0 || grpc != 0 || output.length > 65536 || error.length > 16384
                    || output.length < 5 || output[0] != 0 || ByteBuffer.wrap(output, 1, 4).getInt() != output.length - 5)
                throw new IOException();
            return Arrays.copyOfRange(output, 5, output.length);
        } finally { if (process.isAlive()) process.destroyForcibly(); }
    }

    static long varint(ByteBuffer input) {
        long value = 0;
        for (int offset = 0; offset < 64; offset += 7) {
            int b = Byte.toUnsignedInt(input.get()); value |= (long) (b & 127) << offset;
            if ((b & 128) == 0) return value;
        }
        throw new IllegalArgumentException();
    }

    public static void main(String[] args) {
        try {
            long deadline = System.nanoTime() + Duration.ofSeconds(20).toNanos();
            while (!Files.exists(Path.of("/sidecardir/provider.sock"))) {
                if (System.nanoTime() > deadline) throw new IOException();
                Thread.sleep(250);
            }
            rpc("NewSecretProvider", field("bluemix", 1));
            var request = new ByteArrayOutputStream();
            request.write(new byte[]{0x10, 1}); request.write(field("AM selected cluster managed ingress preparation", 3));
            var response = ByteBuffer.wrap(rpc("GetDefaultIAMToken", request.toByteArray()));
            String token = null; long ttl = 0;
            while (response.hasRemaining()) {
                long tag = varint(response);
                if (tag == 10) {
                    int size = Math.toIntExact(varint(response));
                    if (size > 32768 || size > response.remaining()) throw new IOException();
                    byte[] bytes = new byte[size]; response.get(bytes); token = new String(bytes, StandardCharsets.UTF_8);
                } else if (tag == 16) ttl = varint(response);
                else throw new IOException();
            }
            if (token == null || !token.matches("[a-zA-Z0-9._-]{30,32768}") || ttl <= 120 || ttl > 86400)
                throw new IOException();
            // stdout is the private Kubernetes exec stream, never Pod logs or an API response.
            System.out.println("IAM " + ttl + " " + token);
        } catch (Exception failure) {
            System.err.println("IBM_TOKEN_BRIDGE_FAILED"); System.exit(1);
        }
    }
}
