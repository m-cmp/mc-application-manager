package kr.co.mcmp.util;

import kr.co.mcmp.dto.oss.component.CommonUploadComponent;
import org.apache.commons.fileupload.FileItem;
import org.apache.commons.fileupload.disk.DiskFileItem;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.commons.CommonsMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;

public class FileUtil {

    public static MultipartFile generatedMultipartFile(CommonUploadComponent.TextComponentDto textComponent) throws IOException {
        byte[] content = textComponent.getText().getBytes();
        String fileName = textComponent.getFilename() + "." + textComponent.getExtension();
        String mimeType = getMimeType(textComponent.getExtension());

        FileItem fileItem = new DiskFileItem(
                "file",
                mimeType,
                false,
                fileName,
                content.length,
                null
        );

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(content)) {
            fileItem.getOutputStream().write(inputStream.readAllBytes());
        }

        return new CommonsMultipartFile(fileItem);
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
    
}
