package kr.co.mcmp.util;

public class NamingUtils {

	/**
	 * 젠킨스 Credentials 명명 규칙
	 * @param id
	 * @param name
	 * @return
	 */
    public static String getCredentialName(Long id, String name) {
    	return String.format("m-cmp_%s-%s-Credential", id, name);
    }
}
