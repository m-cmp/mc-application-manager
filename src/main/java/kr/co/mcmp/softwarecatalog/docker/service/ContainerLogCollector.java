package kr.co.mcmp.softwarecatalog.docker.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.async.ResultCallback;
import com.github.dockerjava.api.model.Frame;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class ContainerLogCollector {

    public List<String> collectErrorLogs(DockerClient dockerClient, String containerId) throws InterruptedException {
        return collectLogs(dockerClient, containerId, true);
    }

    public List<String> collectLogs(DockerClient dockerClient, String containerId) throws InterruptedException {
        return collectLogs(dockerClient, containerId, false);
    }

    private List<String> collectLogs(DockerClient dockerClient, String containerId, boolean onlyErrors) throws InterruptedException {
        List<String> logs = new ArrayList<>();
        try (LogContainerCallback logsCallback = new LogContainerCallback(onlyErrors)) {
            dockerClient.logContainerCmd(containerId)
                    .withStdErr(true)
                    .withStdOut(true)
                    .withTail(100)
                    .exec(logsCallback);
            logsCallback.awaitCompletion(30, TimeUnit.SECONDS);
            logs = logsCallback.getLogs();
        } catch (Exception e) {
            log.error("Error fetching container logs", e);
        }
        return logs;
    }

    private static class LogContainerCallback extends ResultCallback.Adapter<Frame> {
        private final List<String> logs = new ArrayList<>();
        private final boolean onlyErrors;

        public LogContainerCallback(boolean onlyErrors) {
            this.onlyErrors = onlyErrors;
        }

        @Override
        public void onNext(Frame item) {
            String logLine = new String(item.getPayload()).trim();
            if (!onlyErrors || logLine.toLowerCase().contains("error") || logLine.toLowerCase().contains("exception")) {
                logs.add(logLine);
            }
        }

        public List<String> getLogs() {
            return logs;
        }
    }
}