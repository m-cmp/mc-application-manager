package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.math.BigInteger;
import java.util.Comparator;

import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.dto.ApplicationStatusDto;

final class ApplicationStatusOrdering {
    private ApplicationStatusOrdering() { }

    static final Comparator<ApplicationStatusDto> ORDER = Comparator
            .comparing(ApplicationStatusDto::getNamespace, ApplicationStatusOrdering::compareIds)
            .thenComparing(a -> a.getDeploymentType() == DeploymentType.K8S ? a.getClusterName() : a.getMciId(),
                    ApplicationStatusOrdering::compareIds)
            .thenComparing(ApplicationStatusDto::getNodeGroupName, ApplicationStatusOrdering::compareIds)
            .thenComparing(ApplicationStatusDto::getVmId, ApplicationStatusOrdering::compareIds)
            .thenComparing(ApplicationStatusDto::getApplicationName, ApplicationStatusOrdering::compareIds)
            .thenComparing(ApplicationStatusDto::getCheckedAt, Comparator.nullsLast(Comparator.reverseOrder()))
            .thenComparing(ApplicationStatusDto::getId, Comparator.nullsLast(Comparator.reverseOrder()));

    private static int compareIds(String left, String right) {
        if (left == right) return 0;
        if (left == null || left.isBlank()) return right == null || right.isBlank() ? 0 : 1;
        if (right == null || right.isBlank()) return -1;
        int i = 0, j = 0;
        while (i < left.length() && j < right.length()) {
            char a = left.charAt(i), b = right.charAt(j);
            if (a >= '0' && a <= '9' && b >= '0' && b <= '9') {
                int startI = i, startJ = j;
                while (i < left.length() && left.charAt(i) >= '0' && left.charAt(i) <= '9') i++;
                while (j < right.length() && right.charAt(j) >= '0' && right.charAt(j) <= '9') j++;
                int result = new BigInteger(left.substring(startI, i)).compareTo(new BigInteger(right.substring(startJ, j)));
                if (result != 0) return result;
            } else {
                int result = Character.compare(Character.toLowerCase(a), Character.toLowerCase(b));
                if (result != 0) return result;
                i++; j++;
            }
        }
        return Integer.compare(left.length() - i, right.length() - j);
    }
}
