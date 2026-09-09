package kr.co.mcmp.softwarecatalog.application.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import kr.co.mcmp.ape.cbtumblebug.api.CbtumblebugRestApi;
import kr.co.mcmp.ape.cbtumblebug.dto.MciDto;
import kr.co.mcmp.ape.cbtumblebug.dto.VmAccessInfo;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.constants.VmDeploymentMode;
import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;
import kr.co.mcmp.softwarecatalog.application.exception.ApplicationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Resolves a CB-Tumblebug VM subGroup into an authoritative VM target list.
 *
 * <p>NodeGroup deployment intentionally supports standalone mode only. The
 * caller-provided VM list is replaced with the currently running members from
 * CB-Tumblebug so a request cannot add VMs from another group.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class VmNodeGroupTargetResolver {

    private final CbtumblebugRestApi cbtumblebugRestApi;

    public void resolve(DeploymentRequest request) {
        if (request == null || StringUtils.isBlank(request.getVmNodeGroupId())) {
            return;
        }
        if (request.getDeploymentType() != DeploymentType.VM) {
            throw new ApplicationException("VM NodeGroup can only be used for VM deployment");
        }
        if (request.getVmDeploymentMode() == VmDeploymentMode.CLUSTERING) {
            throw new ApplicationException("VM NodeGroup deployment supports STANDALONE mode only");
        }
        if (StringUtils.isBlank(request.getNamespace())) {
            throw new ApplicationException("Namespace is required for VM NodeGroup deployment");
        }
        if (StringUtils.isBlank(request.getMciId())) {
            throw new ApplicationException("MCI ID is required for VM NodeGroup deployment");
        }

        String nodeGroupId = request.getVmNodeGroupId().trim();
        MciDto mci = cbtumblebugRestApi.getMciByMciId(request.getNamespace(), request.getMciId());
        if (mci == null || mci.getNode() == null) {
            throw new ApplicationException("VM NodeGroup information is unavailable: " + nodeGroupId);
        }

        boolean groupExists = false;
        Set<String> runningVmIds = new LinkedHashSet<>();
        for (VmAccessInfo vm : mci.getNode()) {
            if (vm == null || !nodeGroupId.equals(StringUtils.trimToEmpty(vm.getSubGroupId()))) {
                continue;
            }

            groupExists = true;
            if (!isRunning(vm)) {
                continue;
            }

            String vmId = firstNonBlank(vm.getId(), vm.getName());
            if (vmId != null) {
                runningVmIds.add(vmId);
            }
        }

        if (!groupExists) {
            throw new ApplicationException("VM NodeGroup was not found in the selected MCI: " + nodeGroupId);
        }
        if (runningVmIds.isEmpty()) {
            throw new ApplicationException("VM NodeGroup has no running VMs: " + nodeGroupId);
        }

        if (request.getVmIds() != null && !request.getVmIds().isEmpty()) {
            log.debug("Replacing caller-provided VM targets with authoritative NodeGroup members: nodeGroupId={}",
                    nodeGroupId);
        }
        request.setVmNodeGroupId(nodeGroupId);
        request.setVmDeploymentMode(VmDeploymentMode.STANDALONE);
        request.setVmIds(new ArrayList<>(runningVmIds));

        log.info("Resolved VM NodeGroup deployment targets: namespace={}, mciId={}, nodeGroupId={}, runningVmCount={}",
                request.getNamespace(), request.getMciId(), nodeGroupId, runningVmIds.size());
    }

    private boolean isRunning(VmAccessInfo vm) {
        return "RUNNING".equalsIgnoreCase(StringUtils.trimToEmpty(vm.getStatus()));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }
}
