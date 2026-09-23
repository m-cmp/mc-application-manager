package kr.co.mcmp.softwarecatalog.application.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.LocalDateTime;
import java.util.Map;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import kr.co.mcmp.softwarecatalog.application.model.ResourceMetricsHistory;

/** Runs the real native SQL and Spring Data projection, not a mocked getter map. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = ResourceMetricsHistoryRepositoryTest.Config.class)
class ResourceMetricsHistoryRepositoryTest {
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = ResourceMetricsHistoryRepository.class,
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = ".*(?<!ResourceMetricsHistory)Repository"))
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource(
                    "jdbc:h2:mem:metrics;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setManagedTypes(PersistenceManagedTypes.of(ResourceMetricsHistory.class.getName()));
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }

    @Autowired ResourceMetricsHistoryRepository repository;
    private final LocalDateTime start = LocalDateTime.of(2026, 9, 20, 0, 0);

    @BeforeEach void clear() { repository.deleteAll(); }

    @Test void mapsEveryAggregateWithExactCamelCaseAliases() {
        repository.saveAndFlush(sample(7L, start, 10.0, 20.0, 100L, 300L, false, "RUNNING"));
        repository.saveAndFlush(sample(7L, start.plusMinutes(10), 30.0, 60.0, 200L, 500L, true, "STOPPED"));
        // Neither other deployments nor a different day may contribute.
        repository.saveAndFlush(sample(8L, start, 99.0, 99.0, 900L, 900L, true, "RUNNING"));
        repository.saveAndFlush(sample(7L, start.plusDays(1), 99.0, 99.0, 900L, 900L, true, "RUNNING"));
        var result = repository.aggregateByDeploymentAndDate(7L, start, start.plusDays(1).minusNanos(1000));
        assertThat(result.getDeploymentId()).isEqualTo(7L);
        assertThat(result.getSampleCount()).isEqualTo(2);
        assertThat(result.getAvgCpuPct()).isEqualTo(20.0);
        assertThat(result.getMaxCpuPct()).isEqualTo(30.0);
        assertThat(result.getP95CpuPct()).isCloseTo(29.0, within(0.001));
        assertThat(result.getStddevCpu()).isCloseTo(Math.sqrt(200), within(0.001));
        assertThat(result.getAvgMemoryPct()).isEqualTo(40.0);
        assertThat(result.getMaxMemoryPct()).isEqualTo(60.0);
        assertThat(result.getP95MemoryPct()).isCloseTo(58.0, within(0.001));
        assertThat(result.getStddevMemory()).isCloseTo(Math.sqrt(800), within(0.001));
        assertThat(result.getAvgNetworkInBytes()).isEqualTo(150.0);
        assertThat(result.getMaxNetworkInBytes()).isEqualTo(200L);
        assertThat(result.getAvgNetworkOutBytes()).isEqualTo(400.0);
        assertThat(result.getMaxNetworkOutBytes()).isEqualTo(500L);
        assertThat(result.getOomCount()).isEqualTo(1);
        assertThat(result.getRunningMinutes()).isEqualTo(10);
        assertThat(result.getTotalMinutes()).isEqualTo(20);
        assertThat(result.getResourceType()).isEqualTo("GENERAL");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 71, 72, 137, 144})
    void mapsCountIncludingEmptyAndValidityThresholds(int samples) {
        for (int i = 0; i < samples; i++) {
            repository.save(sample(7L, start.plusMinutes(i * 10L), 0.0, 0.0, 0L, 0L, false, "RUNNING"));
        }
        repository.flush();
        var result = repository.aggregateByDeploymentAndDate(7L, start, start.plusDays(1).minusNanos(1000));
        assertThat(result.getSampleCount()).isEqualTo(samples);
        assertThat(result.getTotalMinutes()).isEqualTo(samples * 10);
        if (samples > 0) {
            assertThat(result.getRunningMinutes()).isEqualTo(samples * 10);
            assertThat(result.getP95CpuPct()).isZero();
        } else {
            assertThat(result.getP95CpuPct()).isNull();
        }
    }

    @Test void preservesMissingMetricsRatherThanInventingZeroUsage() {
        repository.saveAndFlush(sample(11L, start, null, null, null, null, null, null));
        var result = repository.aggregateByDeploymentAndDate(11L, start, start.plusDays(1));
        assertThat(result.getSampleCount()).isEqualTo(1);
        assertThat(result.getAvgCpuPct()).isNull();
        assertThat(result.getP95MemoryPct()).isNull();
        assertThat(result.getStddevCpu()).isNull();
        assertThat(result.getMaxNetworkInBytes()).isNull();
        assertThat(result.getOomCount()).isZero();
        assertThat(result.getRunningMinutes()).isZero();
    }

    private ResourceMetricsHistory sample(Long id, LocalDateTime time, Double cpu, Double memory,
            Long networkIn, Long networkOut, Boolean oom, String status) {
        return ResourceMetricsHistory.builder().deploymentId(id).recordedAt(time)
                .cpuUsagePct(cpu).memoryUsagePct(memory).networkInBytes(networkIn).networkOutBytes(networkOut)
                .oomKilled(oom).status(status).resourceType("GENERAL").deploymentType("K8S").build();
    }
}
