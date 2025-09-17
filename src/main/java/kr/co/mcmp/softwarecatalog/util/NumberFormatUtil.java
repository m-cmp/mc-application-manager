package kr.co.mcmp.softwarecatalog.util;

public class NumberFormatUtil {
    
    /**
     * 숫자를 K, M 단위로 포맷팅합니다.
     * @param number 포맷팅할 숫자
     * @return 포맷팅된 문자열 (예: 1.2K, 1.5M)
     */
    public static String formatNumber(Long number) {
        if (number == null || number == 0) {
            return "0";
        }
        
        if (number < 1000) {
            return String.valueOf(number);
        } else if (number < 1000000) {
            double k = number / 1000.0;
            if (k == (long) k) {
                return String.format("%.0fK", k);
            } else {
                return String.format("%.1fK", k);
            }
        } else {
            double m = number / 1000000.0;
            if (m == (long) m) {
                return String.format("%.0fM", m);
            } else {
                return String.format("%.1fM", m);
            }
        }
    }
    
    /**
     * 숫자를 K, M 단위로 포맷팅합니다.
     * @param number 포맷팅할 숫자
     * @return 포맷팅된 문자열 (예: 1.2K, 1.5M)
     */
    public static String formatNumber(Integer number) {
        if (number == null || number == 0) {
            return "0";
        }
        return formatNumber((long) number);
    }
}
