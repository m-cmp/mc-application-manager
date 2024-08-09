package kr.co.mcmp.util;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class DateUtil {
	
	
	/**
	 * time 부터 그날의 00:00시 까지의 시간을 구해 ms 단위로 리턴한다.
	 * @param time
	 * @return
	 */
	public static long getTimeMidnight(long time) {
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(time);
		c.add(Calendar.DATE, 0);
		c.set(Calendar.HOUR_OF_DAY, 0);
		c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);        
		return c.getTimeInMillis();
	}
	
	public static long calDate(long time, int ago) {
		Calendar c = Calendar.getInstance();
		c.setTimeInMillis(time);
		c.add(Calendar.DATE, ago);     
		return c.getTimeInMillis();
	}
	
	public static void main(String[] args) {	
		Format format = new SimpleDateFormat("yyyy MM dd HH:mm:ss");
		
		Date dt = new Date();
		long dateMs = dt.getTime();
		long yDay = calDate(dateMs, - 6);			
		
		String a = format.format(dateMs);
		String b = format.format(yDay);
		System.out.println(a + " ~ " + b);
	}
	
}
