package kr.co.mcmp.util;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import javax.annotation.PostConstruct;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Configuration
public class FileUtils implements WebMvcConfigurer  { 
	
	private static String uploadPath;
    private static List<String> allowedExtensions;

	@PostConstruct
    public void init() {
        uploadPath = getUploadPath();
        allowedExtensions = getAllowedExtensions();
    }
	
    @Autowired
    private Environment env;

	@Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/uploads/**")
                .addResourceLocations("file:" + uploadPath);
    }
	private List<String> getAllowedExtensions() {
        String extensions = env.getProperty("file.upload.allowed-extensions");
        return Arrays.asList(extensions.split(","));
    }

    private String getUploadPath() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            return env.getProperty("file.upload.path.windows");
        } else if (os.contains("nix") || os.contains("nux") || os.contains("mac")) {
            return env.getProperty("file.upload.path.linux");
        } else {
            throw new IllegalStateException("지원되지 않는 운영 체제입니다.");
        }
    }

	public static String readLine(InputStream is) throws IOException {
		StringBuffer out = new StringBuffer(); 
		byte[] b = new byte[4096]; 
		for(int n; (n = is.read(b)) != -1;) { 
			out.append(new String(b, 0, n)); 
		} 
		return out.toString();
	}
	
	public static boolean deleteFile(String filePath) {
        if (filePath == null || filePath.isEmpty()) {
            return false;
        }

        try {
            Path path = Paths.get(filePath);
            Files.deleteIfExists(path);
            return true;
        } catch (IOException e) {
			log.error(e.getMessage(), e);
            return false;
        }
    }

	public static String readLineByLineJava8(File file) throws IOException {
		return readLineByLineJava8(file.getAbsolutePath());
	}
	
	/**
	 * 파일 내용을 스트링으로 리턴한다.
	 * @param filePath
	 * @return
	 */
	public static String readLineByLineJava8(String filePath) throws IOException {
		StringBuilder contentBuilder = new StringBuilder();
		Stream<String> stream = Files.lines(Paths.get(filePath), StandardCharsets.UTF_8);
		stream.forEach(s -> contentBuilder.append(s).append("\n"));
		if(stream != null) {
			stream.close();
		}		
		return contentBuilder.toString();
	}
	
	public static void fileWrite(File file, String contents) throws IOException {
        FileWriter writer = null;
        writer = new FileWriter(file, false);
        writer.write(contents);
        writer.flush();
        
        if(writer != null) {
        	writer.close();
        }
	}
	
	public static void writeToFile(File file, byte[] pData) throws IOException {
		FileOutputStream lFileOutputStream = new FileOutputStream(file);
		lFileOutputStream.write(pData);
		lFileOutputStream.close();
	}
	
	public static void fileWrite(String filePath, String contents) throws IOException {
        File f = new File(filePath);
        fileWrite(f, contents);
	}
	
	public static File createDirectory(String directoryPath) {
		return createDirectory(new File(directoryPath));
	}
	
	public static File createDirectory(File directory) {
		if(!directory.isDirectory()) {
			directory.mkdirs();
		}
		return directory;
	}
	
	
	public static String uploadIcon(MultipartFile iconFile) throws IOException {
        String originalFilename = iconFile.getOriginalFilename();
        if (StringUtils.isEmpty(originalFilename)) {
            throw new IllegalArgumentException("파일 이름이 없습니다.");
        }

        String fileExtension = getFileExtension(originalFilename);
        if (!allowedExtensions.contains(fileExtension.substring(1))) {
            throw new IllegalArgumentException("허용되지 않는 파일 형식입니다. " + 
                String.join(", ", allowedExtensions) + "만 허용됩니다.");
        }

        String uniqueFilename = UUID.randomUUID().toString() + fileExtension;

        Path fullUploadPath = Paths.get(uploadPath, "images").toAbsolutePath().normalize();
        Files.createDirectories(fullUploadPath);

		Path targetLocation = fullUploadPath.resolve(uniqueFilename);
		Files.copy(iconFile.getInputStream(), targetLocation, StandardCopyOption.REPLACE_EXISTING);
	
		return "/uploads/images/" + uniqueFilename;
    }

    private static String getFileExtension(String filename) {
        return filename.substring(filename.lastIndexOf(".")).toLowerCase();
    }
	
}
