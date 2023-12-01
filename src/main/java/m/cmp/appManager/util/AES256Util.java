package m.cmp.appManager.util;

import java.io.UnsupportedEncodingException;
import java.security.GeneralSecurityException;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AES256Util {
	
	private static String authAesKey; 

	@Value("${aes.key}")
    public void setAuthAesKey(String aesKey) {
		authAesKey = aesKey;
    }	

	public static String encrypt(String str) {
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
			log.error("[AES256Utils] encrypt error >>>>>>>>>>>>>>>>>> ", e);
			return null;
		}

		return enStr;
	}

	public static String decrypt(String str){
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
			log.error("[AES256Utils] decrypt error >>>>>>>>>>>>>>>>>> ", e);
			return null;
		}

		return deStr;
	}
}
