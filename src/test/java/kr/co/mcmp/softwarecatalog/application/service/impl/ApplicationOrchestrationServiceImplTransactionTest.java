package kr.co.mcmp.softwarecatalog.application.service.impl;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import kr.co.mcmp.softwarecatalog.application.dto.DeploymentRequest;

class ApplicationOrchestrationServiceImplTransactionTest {

    @Test
    void deployApplicationDoesNotHoldOuterTransactionWhileStartingAsyncVmDeployment() throws Exception {
        Method method = ApplicationOrchestrationServiceImpl.class
                .getMethod("deployApplication", DeploymentRequest.class);

        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.NOT_SUPPORTED);
    }
}
