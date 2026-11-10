package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.MciDto;
import kr.co.mcmp.ape.cbtumblebug.dto.VmAccessInfo;
import kr.co.mcmp.ape.cbtumblebug.exception.CbtumblebugException;
import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.model.ApplicationStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Read-only Tumblebug snapshots shared by monitoring and the status list. */
@Service
@RequiredArgsConstructor
@Slf4j
public class VmInfraStateService {
    private final CbtumblebugRestApi tumblebug;
    private volatile Map<InfraKey, InfraState> states = Map.of();

    public void refresh(List<ApplicationStatus> applications) {
        Map<InfraKey, InfraState> refreshed = new HashMap<>();
        applications.stream().filter(a -> a.getDeploymentType() == DeploymentType.VM)
                .map(a -> new InfraKey(a.getNamespace(), a.getMciId()))
                .filter(InfraKey::valid).distinct()
                .forEach(key -> refreshed.put(key, load(key)));
        // Replace unknown/failed reads too: an outage must never hide old rows.
        states = Map.copyOf(refreshed);
    }

    public boolean isDeleted(String namespace, String infraId) {
        return state(namespace, infraId).deleted();
    }

    public VmAccessInfo findVm(String namespace, String infraId, String vmId) {
        return state(namespace, infraId).findVm(vmId);
    }

    public void assertActionAllowed(ApplicationStatus application, ActionType action) {
        if (action != ActionType.RESTART && action != ActionType.STOP && action != ActionType.UNINSTALL) {
            return;
        }
        InfraState current = load(new InfraKey(application.getNamespace(), application.getMciId()));
        if (current.deleted()) {
            throw new IllegalStateException("The target Infra no longer exists.");
        }
        if (current.infra() == null || current.infra().getNode() == null) {
            throw new IllegalStateException("Unable to verify the target VM state. Please retry.");
        }
        VmAccessInfo vm = current.findVm(application.getVmId());
        if (vm == null) {
            throw new IllegalStateException("The target VM was not found in the Infra.");
        }
        if (isSuspended(vm)) {
            throw new IllegalStateException("VM " + application.getVmId()
                    + " is Suspended. Start the VM before performing this operation.");
        }
    }

    public static boolean isSuspended(VmAccessInfo vm) {
        return vm != null && "SUSPENDED".equalsIgnoreCase(
                Objects.toString(vm.getStatus(), "").trim());
    }

    private InfraState state(String namespace, String infraId) {
        return states.getOrDefault(new InfraKey(namespace, infraId), InfraState.UNKNOWN);
    }

    private InfraState load(InfraKey key) {
        if (!key.valid()) return InfraState.UNKNOWN;
        try {
            MciDto infra = tumblebug.getMciByMciId(key.namespace(), key.infraId());
            return infra != null && key.infraId().equals(infra.getId())
                    ? new InfraState(infra, false) : InfraState.UNKNOWN;
        } catch (CbtumblebugException e) {
            // Tumblebug 0.13.5 reports an absent Infra as HTTP 500. Match its
            // exact resource-specific message, never a generic 404/500 or timeout.
            if (isMissingInfra(e, key.infraId())) return new InfraState(null, true);
            log.warn("Cannot verify Infra {}/{} (HTTP {})", key.namespace(), key.infraId(), e.getCode());
        } catch (Exception e) {
            log.warn("Cannot verify Infra {}/{} ({})", key.namespace(), key.infraId(), e.getClass().getSimpleName());
        }
        return InfraState.UNKNOWN;
    }

    private boolean isMissingInfra(CbtumblebugException error, String infraId) {
        if (error.getCode() != 404 && error.getCode() != 500) return false;
        try {
            return ("The infra " + infraId + " does not exist.").equals(
                    new ObjectMapper().readTree(error.getMessage()).path("message").asText());
        } catch (Exception ignored) {
            return false;
        }
    }

    private record InfraKey(String namespace, String infraId) {
        boolean valid() {
            return namespace != null && !namespace.isBlank() && infraId != null && !infraId.isBlank();
        }
    }

    private record InfraState(MciDto infra, boolean deleted) {
        private static final InfraState UNKNOWN = new InfraState(null, false);

        VmAccessInfo findVm(String vmId) {
            if (infra == null || infra.getNode() == null || vmId == null) return null;
            return infra.getNode().stream().filter(Objects::nonNull)
                    .filter(vm -> vmId.equals(vm.getId())).findFirst().orElse(null);
        }
    }
}
