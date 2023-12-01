package m.cmp.appManager.util;

import java.io.UnsupportedEncodingException;
import java.util.Base64;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Base64Utils {

    /**
     * Base64 Decoding.
     * @param encodedString
     * @return
     */
    public static String base64Decoding(String encodedString) {
        return base64Decoding(encodedString, "UTF-8");
    }

    public static String base64Decoding(String encodedString, String charset) {
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] decodedBytes1 = decoder.decode(encodedString.getBytes());
        String decodedString = null;
        try {
            decodedString = new String(decodedBytes1, charset);
        } catch (UnsupportedEncodingException e) {
            log.error("", e);
        }
        return decodedString;
    }

    /**
     * Base64 encoding.
     * @param text
     * @return
     */
    public static String base64Encoding(String text) {
        return base64Encoding(text, "UTF-8");
    }


    public static String base64Encoding(String text, String charset) {
        String encodedString = null;
        Base64.Encoder encoder = Base64.getEncoder();
        try {
            byte[] targetByte = text.getBytes(charset);
            encodedString = encoder.encodeToString(targetByte);
        } catch (UnsupportedEncodingException e) {
            log.error("", e);
        }
        return encodedString;
    }
}

