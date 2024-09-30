package kr.co.mcmp.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.UUID;
import java.util.stream.Stream;

import org.apache.commons.lang3.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FileUtils { 
	
	private static final String ICON_UPLOAD_DIR = "src/main/resources/static/images/";

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
		 /* 파일 확장자 검사 */
		 String originalFilename = iconFile.getOriginalFilename();
		 if (StringUtils.isBlank(originalFilename)) {
			 throw new IllegalArgumentException("파일 이름이 없습니다.");
		 }
		 /* 파일 크기 제한 (예: 50MB) */
		//  long maxSize = 50 * 1024 * 1024; // 50MB in bytes
		//  if (iconFile.getSize() > maxSize) {
		// 	 throw new IllegalArgumentException("파일 크기는 50MB를 초과할 수 없습니다.");
		//  }
		 String fileExtension = originalFilename.substring(originalFilename.lastIndexOf(".")).toLowerCase();
		 if (!Arrays.asList(".jpg", ".jpeg", ".png", ".gif").contains(fileExtension)) {
			 throw new IllegalArgumentException("허용되지 않는 파일 형식입니다. jpg, jpeg, png, gif만 허용됩니다.");
		 }
        String uniqueFilename = UUID.randomUUID().toString() + fileExtension; 
		/* 업로드할 전체 경로 생성 */
        Path uploadPath = Paths.get(ICON_UPLOAD_DIR).toAbsolutePath().normalize();  
        File destFile = new File(uploadPath.toFile(), uniqueFilename);
		/* 디렉토리가 존재하지 않으면 생성 */
        org.apache.commons.io.FileUtils.forceMkdirParent(destFile); 
        
        org.apache.commons.io.FileUtils.copyInputStreamToFile(iconFile.getInputStream(), destFile); 
        
        // 저장된 파일의 상대 경로 반환
        return "/images/" + uniqueFilename;
    }
	public static void copy(File sourceLocation, File targetLocation)	throws IOException {
		if (sourceLocation.isDirectory()) {
			if (!targetLocation.isDirectory()) {
				targetLocation.mkdirs();
			}
			String[] children = sourceLocation.list();
			for (int i = 0; i < children.length; i++) {
				copy(new File(sourceLocation, children[i]), new File(
						targetLocation, children[i]));
			}
		} 
		else {
			InputStream in = new FileInputStream(sourceLocation);
			copy(in, targetLocation);
		}
	}
	
	public static void copy(InputStream in, File targetLocation)	throws IOException {
		OutputStream out = new FileOutputStream(targetLocation);

		byte[] buf = new byte[1024];
		int len = 0;
		while ((len = in.read(buf)) > 0) {
			out.write(buf, 0, len);
		}
		in.close();
		out.close();
	}
	
	
	public static void deleteDirectory(File file) throws IOException {
		org.apache.commons.io.FileUtils.deleteDirectory(file);
	}
	
}
