package kr.co.mcmp.util;

import kr.co.mcmp.dto.oss.component.CommonUploadComponent;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;

public class FileUtil {

    public static MultipartFile generatedMultipartFile(CommonUploadComponent.TextComponentDto textComponent) throws IOException {
        byte[] content = textComponent.getText().getBytes();
        String fileName = textComponent.getFilename() + "." + textComponent.getExtension();
        String mimeType = getMimeType(textComponent.getExtension());

        return new InMemoryMultipartFile(fileName, mimeType, content);
    }

    private static String getMimeType(String extension) {
        switch (extension.toLowerCase()) {
            case "txt":
                return "text/plain";
            case "html":
                return "text/html";
            case "sh":
                return "application/x-sh";
            case "yaml":
                return "text/x-yaml";
            default:
                return "application/octet-stream";
        }
    }

    /**
     * In-memory MultipartFile implementation to replace commons-fileupload based CommonsMultipartFile.
     * Spring 6 (Boot 3) removed CommonsMultipartFile from the web module.
     */
    private static class InMemoryMultipartFile implements MultipartFile {
        private final String name;
        private final String contentType;
        private final byte[] content;

        InMemoryMultipartFile(String name, String contentType, byte[] content) {
            this.name = name;
            this.contentType = contentType;
            this.content = content;
        }

        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return name; }
        @Override public String getContentType() { return contentType; }
        @Override public boolean isEmpty() { return content == null || content.length == 0; }
        @Override public long getSize() { return content.length; }
        @Override public byte[] getBytes() { return content; }
        @Override public InputStream getInputStream() { return new ByteArrayInputStream(content); }
        @Override public void transferTo(File dest) throws IOException {
            try (FileOutputStream out = new FileOutputStream(dest)) {
                out.write(content);
            }
        }
    }
}
