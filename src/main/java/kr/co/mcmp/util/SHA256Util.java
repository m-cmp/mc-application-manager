package kr.co.mcmp.util;


import java.math.BigInteger;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;


public class SHA256Util {
	
	public static String hash(String str, String key) throws NoSuchAlgorithmException {
		
		MessageDigest md = MessageDigest.getInstance("SHA-256");
		md.update((str + key).getBytes());
		String hex = String.format("%064x", new BigInteger(1, md.digest()));
		
		return hex;
	}
}
