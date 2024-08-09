package kr.co.mcmp.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.RegExUtils;
import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

public class OpenShiftUtils {
	
	/*
	 * Image 이름에서 ImageStreamTag 를 생성  
	 */
	public static String genImageStreamTag(String imageName) {
		String imageStreamTag = null;
		
		if(StringUtils.isNotBlank(imageName)) {
			String lastName = RegExUtils.removeAll(imageName, ".*/");
			imageStreamTag = StringUtils.replace(lastName, ":", "-");
		}
		
		return imageStreamTag;
	}
	
	/*
	 * UTC 시간에서 로컬 시간으로 변경
	 */
	public static String convDateFormatUtcToLocal(String utc) throws ParseException {
		return convDateFormatUtcToLocal(utc, null, "yyyy-MM-dd HH:mm:ss", null);
	}
	public static String convDateFormatUtcToLocal(String utc, String utcPattern, String localPattern, String localTimeZone) throws ParseException {
		if(StringUtils.isBlank(utc)) {
			return null;
		}
		
		if(StringUtils.isBlank(utcPattern)) {
			utcPattern = "yyyy-MM-dd'T'HH:mm:ss'Z'";
		}
		if(StringUtils.isBlank(localPattern)) {
			localPattern = "yyyy-MM-dd HH:mm:ss z";
		}
		if(StringUtils.isBlank(localTimeZone)) {
			localTimeZone = "Asia/Seoul";
		}
		
		DateFormat utcDateFormat = new SimpleDateFormat(utcPattern);
		utcDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
		
		Date date = utcDateFormat.parse(utc);
		
		DateFormat localDateFormat = new SimpleDateFormat(localPattern);
		localDateFormat.setTimeZone(TimeZone.getTimeZone(localTimeZone));
		String local = localDateFormat.format(date);
		
		return local;
	}
	
	/*
	 * byte 에서 size 로 변경. 1024 단위 계산.
	 */
    public static String byteCountToSize(long bytes) {
    	int unit = 1024;
    	int scale = 1;
    	
    	BigDecimal KiB = BigDecimal.valueOf(unit);
    	BigDecimal MiB = KiB.multiply(KiB);
    	BigDecimal GiB = MiB.multiply(KiB);
    	BigDecimal TiB = GiB.multiply(KiB);
    	BigDecimal PiB = TiB.multiply(KiB);
    	
    	BigDecimal s = BigDecimal.valueOf(bytes);
    	String size = null;
    	
    	if(s.divide(PiB, 0, RoundingMode.DOWN).compareTo(BigDecimal.ZERO) > 0) {
    		size = s.divide(PiB, scale, RoundingMode.HALF_UP).toString() + " PiB";
    	} else if(s.divide(TiB, 0, RoundingMode.DOWN).compareTo(BigDecimal.ZERO) > 0) {
    		size = s.divide(TiB, scale, RoundingMode.HALF_UP).toString() + " TiB";
    	} else if(s.divide(GiB, 0, RoundingMode.DOWN).compareTo(BigDecimal.ZERO) > 0) {
    		size = s.divide(GiB, scale, RoundingMode.HALF_UP).toString() + " GiB";
    	} else if(s.divide(MiB, 0, RoundingMode.DOWN).compareTo(BigDecimal.ZERO) > 0) {
    		size = s.divide(MiB, scale, RoundingMode.HALF_UP).toString() + " MiB";
    	} else if(s.divide(KiB, 0, RoundingMode.DOWN).compareTo(BigDecimal.ZERO) > 0) {
    		size = s.divide(KiB, scale, RoundingMode.HALF_UP).toString() + " KiB";
    	} else {
    		size = s.toString() + " iB"; 
    	}
    	
    	return size;
    }
    
    @SuppressWarnings("unchecked")
    public static <T> T objectConcat(Class<T> clazz, T... sources) {
    	ObjectMapper mapper = new ObjectMapper();
    	
    	Map<String, Object> targetMap = new LinkedHashMap<>();
    	for(T source : sources) {
			Map<String, Object> sourceMap = mapper.convertValue(source, LinkedHashMap.class);
			targetMap = Stream.concat(targetMap.entrySet().stream(), sourceMap.entrySet().stream())
								.filter(m -> m.getValue() != null)
								.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (v1, v2) -> v1 != null ? v1 : v2, LinkedHashMap::new));
    	}
    	
    	T target = mapper.convertValue(targetMap, clazz);
    	return target;
    }

}
