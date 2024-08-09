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
import java.nio.file.Paths;
import java.util.stream.Stream;

public class FileUtils { 
	
	public static String readLine(InputStream is) throws IOException {
		StringBuffer out = new StringBuffer(); 
		byte[] b = new byte[4096]; 
		for(int n; (n = is.read(b)) != -1;) { 
			out.append(new String(b, 0, n)); 
		} 
		return out.toString();
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
