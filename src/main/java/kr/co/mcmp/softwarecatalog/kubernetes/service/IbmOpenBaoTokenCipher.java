package kr.co.mcmp.softwarecatalog.kubernetes.service;

import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/** The random encryption key lives on the existing AM persistent mount, outside the database/image. */
final class IbmOpenBaoTokenCipher {
    static final String DEFAULT_KEY_FILE = "/nexus-data/.am/ibm-openbao.key";
    private final Path keyFile;
    private final SecureRandom random = new SecureRandom();

    IbmOpenBaoTokenCipher(String path) {
        keyFile = Path.of(path);
        if (!keyFile.isAbsolute()) throw new IllegalArgumentException("Configure an absolute IBM credential encryption key path.");
    }

    String encrypt(String value, String binding) {
        try {
            byte[] iv = new byte[12]; random.nextBytes(iv);
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key(true), "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            return "v1:" + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array());
        } catch (Exception e) { throw unavailable(); }
    }

    String decrypt(String value, String binding) {
        try {
            if (!value.startsWith("v1:") || value.length() > 16384) throw new IllegalArgumentException();
            byte[] packed = Base64.getDecoder().decode(value.substring(3));
            if (packed.length < 29) throw new IllegalArgumentException();
            var cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key(false), "AES"), new GCMParameterSpec(128, packed, 0, 12));
            cipher.updateAAD(binding.getBytes(StandardCharsets.UTF_8));
            return new String(cipher.doFinal(packed, 12, packed.length - 12), StandardCharsets.UTF_8);
        } catch (Exception e) { throw unavailable(); }
    }

    private byte[] key(boolean create) throws Exception {
        if (create && !Files.exists(keyFile, LinkOption.NOFOLLOW_LINKS)) {
            var parent = keyFile.getParent();
            if (Files.getFileStore(parent.getRoot()).supportsFileAttributeView("posix")) {
                Files.createDirectories(parent, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
            } else Files.createDirectories(parent);
            byte[] bytes = new byte[32]; random.nextBytes(bytes);
            try (var channel = Files.getFileStore(parent).supportsFileAttributeView("posix")
                    ? FileChannel.open(keyFile, java.util.Set.of(StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE),
                        PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")))
                    : FileChannel.open(keyFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
                var data = ByteBuffer.wrap(bytes);
                while (data.hasRemaining()) channel.write(data);
                channel.force(true);
            } catch (FileAlreadyExistsException ignored) { /* Another operator created the key. Never replace it. */ }
        }
        if (Files.isSymbolicLink(keyFile) || !Files.isRegularFile(keyFile) || Files.size(keyFile) != 32) throw unavailable();
        return Files.readAllBytes(keyFile);
    }

    private static IllegalArgumentException unavailable() {
        return new IllegalArgumentException("IBM credential encryption key is missing, changed or unreadable. Restore the original persistent key; existing secrets are not overwritten.");
    }
}
