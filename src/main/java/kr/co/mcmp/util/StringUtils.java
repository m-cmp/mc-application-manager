package kr.co.mcmp.util;

public class StringUtils {
	
	
	/**
	 * 특수문자 기준으로 소문자 시작의 camel 표기법으로 변환
	 * @param str, sep
	 * @return String
	 */
	public static String toLowerCamelCase(String str, String sep) {
		String camel = "";
		
		if(sep == null) {
			sep = "_";
		}
		
		if(str != null && !"".equals(str)) {
			String[] strs = str.split(sep);
			if(strs.length > 1) {
				camel = strs[0];
				for(int i=1; i<strs.length; i++) {
					camel += strs[i].substring(0, 1).toUpperCase() + strs[i].substring(1);
				}
			}
		}
		
		if("".equals(camel)) {
			camel = str;
		}
		
		return camel;
	}
	
}
