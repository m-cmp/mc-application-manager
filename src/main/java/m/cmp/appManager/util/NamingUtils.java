package m.cmp.appManager.util;

public class NamingUtils {

	/**
	 * 젠킨스 Credentials 명명 규칙
	 * @param ossName
	 * @return
	 */
    public static String getCredentialName(int id, String name) {
    	return String.format("m-cmp_%s-%s-Credential", id, name);
    }
}
