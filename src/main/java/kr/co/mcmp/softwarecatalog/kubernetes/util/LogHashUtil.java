package kr.co.mcmp.softwarecatalog.kubernetes.util;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 로그 중복 방지를 위한 해시 유틸리티
 */
public class LogHashUtil {
    
    /**
     * 로그 리스트의 해시를 계산합니다.
     * 최근 N개 로그만을 기준으로 해시를 계산하여 중복을 방지합니다.
     */
    public static String calculateLogsHash(List<String> logs, int maxLogs) {
        if (logs == null || logs.isEmpty()) {
            return "";
        }
        
        // 최근 N개 로그만 선택 (시간순으로 정렬된 상태라고 가정)
        List<String> recentLogs = logs.stream()
                .limit(maxLogs)
                .collect(Collectors.toList());
        
        // 로그 내용을 하나의 문자열로 결합
        String combinedLogs = String.join("\n", recentLogs);
        
        // MD5 해시 계산
        return calculateMD5Hash(combinedLogs);
    }
    
    /**
     * 새로운 로그와 기존 로그를 비교하여 실제로 새로운 로그만 추출합니다.
     * @param newLogs 새로 수집된 로그
     * @param existingLogs 기존에 저장된 로그
     * @return 실제로 새로운 로그만 포함된 리스트
     */
    public static List<String> extractNewLogs(List<String> newLogs, List<String> existingLogs) {
        if (existingLogs == null || existingLogs.isEmpty()) {
            return newLogs;
        }
        
        if (newLogs == null || newLogs.isEmpty()) {
            return new ArrayList<>();
        }
        
        // 기존 로그의 마지막 몇 개를 기준으로 중복 제거
        int existingSize = existingLogs.size();
        int checkSize = Math.min(15, existingSize); // 기존 로그의 마지막 15개와 비교
        
        List<String> existingTail = existingLogs.subList(
            Math.max(0, existingSize - checkSize), 
            existingSize
        );
        
        List<String> newUniqueLogs = new ArrayList<>();
        
        // 새로운 로그에서 기존 로그와 중복되지 않는 것만 추출
        for (String newLog : newLogs) {
            boolean isDuplicate = false;
            for (String existingLog : existingTail) {
                if (newLog.equals(existingLog)) {
                    isDuplicate = true;
                    break;
                }
            }
            if (!isDuplicate) {
                newUniqueLogs.add(newLog);
            }
        }
        
        return newUniqueLogs;
    }
    
    /**
     * 로그가 실제로 변경되었는지 확인합니다. (개선된 버전)
     * 새로운 로그가 있는지 확인합니다.
     */
    public static boolean hasNewLogs(List<String> newLogs, List<String> existingLogs) {
        List<String> uniqueNewLogs = extractNewLogs(newLogs, existingLogs);
        return !uniqueNewLogs.isEmpty();
    }
    
    /**
     * 로그 리스트의 해시를 계산합니다. (기본값: 최근 20개)
     */
    public static String calculateLogsHash(List<String> logs) {
        return calculateLogsHash(logs, 20);
    }
    
    /**
     * MD5 해시를 계산합니다.
     */
    private static String calculateMD5Hash(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] hashBytes = md.digest(input.getBytes());
            
            StringBuilder sb = new StringBuilder();
            for (byte b : hashBytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // MD5가 지원되지 않는 경우 입력 문자열의 해시코드 사용
            return String.valueOf(input.hashCode());
        }
    }
    
    /**
     * 두 해시가 같은지 비교합니다.
     */
    public static boolean isHashEqual(String hash1, String hash2) {
        if (hash1 == null && hash2 == null) {
            return true;
        }
        if (hash1 == null || hash2 == null) {
            return false;
        }
        return hash1.equals(hash2);
    }
    
    /**
     * 로그가 변경되었는지 확인합니다.
     */
    public static boolean hasLogsChanged(List<String> newLogs, String existingHash) {
        String newHash = calculateLogsHash(newLogs);
        return !isHashEqual(newHash, existingHash);
    }
}
