package kr.co.mcmp.util;

import java.io.UnsupportedEncodingException;
import java.util.Base64;
import java.util.Base64.Decoder;
import java.util.Base64.Encoder;

public class Base64Util {
	/**
	 * Base64 Decoding.
	 * @param encodedString
	 * @return
	 */
	public static String base64Decoding(String encodedString) {
		return base64Decoding(encodedString, "UTF-8");
	}
	
	public static String base64Decoding(String encodedString, String charset) {
		Decoder decoder = Base64.getDecoder();
		byte[] decodedBytes1 = decoder.decode(encodedString.getBytes());
		String decodedString = null;
		try {
			decodedString = new String(decodedBytes1, charset);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return decodedString;
	}
	
	public static String base64Encoding(String text) {
		return base64Encoding(text, "UTF-8");
	}
	
	public static String base64Encoding(String text, String charset) {
		String encodedString = null;
		Encoder encoder = Base64.getEncoder();
		try {
			byte[] targetByte = text.getBytes(charset);	
			encodedString = encoder.encodeToString(targetByte);
		} catch (UnsupportedEncodingException e) {
			e.printStackTrace();
		}
		return encodedString; 
	}	
}
