package kr.co.mcmp.util;

public class JenkinsPipelineUtil {

	/**
	 * 한 라인을 스트링버퍼에 저장한다.
	 * @param sb
	 * @param line
	 * @return
	 */
	public static StringBuffer appendLine(StringBuffer sb, String line) {
		return appendLine(sb, line, 0);
	}
	
	public static StringBuffer appendLine(StringBuffer sb, String line, int indent) {
		for(int i=0; i<indent * 2; i++) { //\t pipeline error로 공백 * 2로 변경 22.11.09
			sb.append(" ");
		}
		sb.append(line);
		sb.append("\n");
		return sb;
	}
}
