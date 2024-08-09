package kr.co.mcmp.util;

public class ByteUtils {
	
	public static String getSize(long size) {
        String s = "";
        double kb = Math.round(size / 1024);
        double mb = Math.round(kb / 1024);
        double gb = Math.round(kb / 1024);
        double tb = Math.round(kb / 1024);
        if(size < 1024) { 
            s = size + " Bytes";
        } else if(size >= 1024 && size < (1024 * 1024)) {
            s =  String.format("%.0f", kb) + "KB";
        } else if(size >= (1024 * 1024) && size < (1024 * 1024 * 1024)) {
            s = String.format("%.0f", mb) + "MB";
        } else if(size >= (1024 * 1024 * 1024) && size < (1024 * 1024 * 1024 * 1024)) {
            s = String.format("%.0f", gb) + "GB";
        } else if(size >= (1024 * 1024 * 1024 * 1024)) {
            s = String.format("%.0f", tb) + "TB";
        }
        return s;
    }
	
}
