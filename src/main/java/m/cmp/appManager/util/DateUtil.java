package m.cmp.appManager.util;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DateUtil {

    public static String DateTimeToCron(String dateTime){
    	LocalDateTime time = LocalDateTime.parse(dateTime, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    	return DateTimeToCron(time);
    }

    
    public static String DateTimeToCron(LocalDateTime time){
        return String.format("%s %s %s %s %s %s %s", time.getSecond(), time.getMinute(), time.getHour(),
                time.getDayOfMonth(), time.getMonthValue(), "?", time.getYear());
    }
}
