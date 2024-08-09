package kr.co.mcmp.util;


import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

import org.apache.commons.lang3.StringUtils;

public class NumberUtil {

	public static class Unit {

		private static final String BYTE_UNIT	= "1024";
		private static final String NUMBER_UNIT	= "1000";

		private static final int scale = 0;

		private static BigDecimal convertMegaToGiga(Number m, int scale, String unit) {
			if (m == null) {
				return null;
			}
			return new BigDecimal(m.toString()).divide(new BigDecimal(unit), scale, RoundingMode.HALF_UP);
		}

//		private static String MtoG(Number m, int scale, String unit) {
//			BigDecimal giga = convertMegaToGiga(m, scale, unit);
//			if (giga == null) {
//				return null;
//			}
//			if (giga.signum() == 0) {
//				return "0";
//			}
//			//return giga.stripTrailingZeros().toPlainString();		// trailing zero 제거
//			return giga.toPlainString();
//		}

		private static String commaMtoG(Number m, int scale, String unit) {
			BigDecimal giga = convertMegaToGiga(m, scale, unit);
			if (giga == null) {
				return null;
			}
			if (giga.signum() == 0) {
				return "0";
			}
			//String padding = "#";		// trailing zero 제거
			String padding = "0";
			String format = "#,##0" + (scale > 0 ? "." + StringUtils.leftPad("", scale, padding) : "");
			DecimalFormat df = new DecimalFormat(format);
			return df.format(giga);
		}

		// Byte - 1024
		public static String mbToGb(Number mB) {
			return commaMtoG(mB, scale, BYTE_UNIT);
		}

		public static String mbToGb(Number mB, int scale) {
			return commaMtoG(mB, scale, BYTE_UNIT);
		}

		// Hz 1000
		public static String mHzToGHz(Number mHz) {
			return commaMtoG(mHz, scale, NUMBER_UNIT);
		}

		public static String mHzToGHz(Number mHz, int scale) {
			return commaMtoG(mHz, scale, NUMBER_UNIT);
		}

	}

}
