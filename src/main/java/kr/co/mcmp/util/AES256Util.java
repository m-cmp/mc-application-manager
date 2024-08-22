package kr.co.mcmp.util;


import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;
import java.security.Key;
import java.security.NoSuchAlgorithmException;


public class AES256Util {
	private static String iv;
	private static Key keySpec;
	private static String authAesKey;

	@Value("${aes.key}")
	public void setAuthAesKey(String aesKey) {
		authAesKey = aesKey;
	}

	public AES256Util(String authAesKey) throws UnsupportedEncodingException {
		this.iv = authAesKey.substring(0, 16);
		byte[] keyBytes = new byte[16];
		byte[] b = authAesKey.getBytes("UTF-8");
		int len = b.length;
		if (len > keyBytes.length) {
			len = keyBytes.length;
		}
		System.arraycopy(b, 0, keyBytes, 0, len);
		SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

		this.keySpec = keySpec;
	}

	public static String encrypt(String str) throws NoSuchAlgorithmException,
			GeneralSecurityException, UnsupportedEncodingException {
		Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
		c.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv.getBytes()));
		byte[] encrypted = c.doFinal(str.getBytes("UTF-8"));
		String enStr = new String(Base64.encodeBase64(encrypted));
		return enStr;
	}

	public static String decrypt(String str) throws NoSuchAlgorithmException,
			GeneralSecurityException, UnsupportedEncodingException {
		Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
		c.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv.getBytes()));
		byte[] byteStr = Base64.decodeBase64(str.getBytes());
		return new String(c.doFinal(byteStr), "UTF-8");
	}

	public static String encryptOssPassword(String str) {
		String enStr = null;

		try {
			String iv = authAesKey.substring(0, 16);
			byte[] keyBytes = new byte[16];
			byte[] b = authAesKey.getBytes("UTF-8");
			int len = b.length;
			if (len > keyBytes.length) {
				len = keyBytes.length;
			}
			System.arraycopy(b, 0, keyBytes, 0, len);
			SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

			Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
			c.init(Cipher.ENCRYPT_MODE, keySpec, new IvParameterSpec(iv.getBytes()));
			byte[] encrypted = c.doFinal(str.getBytes("UTF-8"));
			enStr = new String(Base64.encodeBase64(encrypted));

		} catch (GeneralSecurityException | UnsupportedEncodingException e ) {
			return null;
		}

		return enStr;
	}

	public static String decryptOssPassword(String str){
		String deStr = null;

		try {
			String iv = authAesKey.substring(0, 16);
			byte[] keyBytes = new byte[16];
			byte[] b = authAesKey.getBytes("UTF-8");
			int len = b.length;
			if (len > keyBytes.length) {
				len = keyBytes.length;
			}
			System.arraycopy(b, 0, keyBytes, 0, len);
			SecretKeySpec keySpec = new SecretKeySpec(keyBytes, "AES");

			Cipher c = Cipher.getInstance("AES/CBC/PKCS5Padding");
			c.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv.getBytes()));
			byte[] byteStr = Base64.decodeBase64(str.getBytes());
			deStr = new String(c.doFinal(byteStr), "UTF-8");
		}catch (GeneralSecurityException | UnsupportedEncodingException e ) {
			return null;
		}

		return deStr;
	}
}
