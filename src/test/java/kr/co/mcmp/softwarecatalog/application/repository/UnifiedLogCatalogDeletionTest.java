package kr.co.mcmp.softwarecatalog.application.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.Map;
import javax.sql.DataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceContext;
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
import org.springframework.transaction.annotation.Transactional;
import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.PortMapping;
import kr.co.mcmp.softwarecatalog.Ref.CatalogRefEntity;
import kr.co.mcmp.softwarecatalog.application.constants.ActionType;
import kr.co.mcmp.softwarecatalog.application.constants.DeploymentType;
import kr.co.mcmp.softwarecatalog.application.model.*;
import kr.co.mcmp.softwarecatalog.users.Entity.User;

/** Real Hibernate JPQL/FK regression, with no external application configuration. */
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = UnifiedLogCatalogDeletionTest.Config.class)
@Transactional
class UnifiedLogCatalogDeletionTest {
    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = UnifiedLogRepository.class,
            excludeFilters = @ComponentScan.Filter(type = FilterType.REGEX,
                    pattern = ".*(?<!UnifiedLog)Repository"))
    static class Config {
        @Bean DataSource dataSource() {
            return new DriverManagerDataSource("jdbc:h2:mem:catalog-log-delete;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource source) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(source);
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setManagedTypes(PersistenceManagedTypes.of(
                    UnifiedLog.class.getName(), DeploymentHistory.class.getName(), ApplicationStatus.class.getName(),
                    SoftwareCatalog.class.getName(), PackageInfo.class.getName(), HelmChart.class.getName(),
                    PortMapping.class.getName(), CatalogRefEntity.class.getName(), User.class.getName()));
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
    }

    @Autowired UnifiedLogRepository logs;
    @PersistenceContext EntityManager em;

    @ParameterizedTest
    @ValueSource(strings = {"deployment-only", "status-only", "both", "mixed"})
    void deletesMatchingLogsWithEitherRelationshipAndPreservesOtherCatalogs(String shape) {
        var target = catalog("target");
        var protectedCatalog = catalog("protected");
        var targetDeployment = deployment(target);
        var protectedDeployment = deployment(protectedCatalog);
        var targetStatus = status(target);
        var protectedStatus = status(protectedCatalog);
        var owned = log(
                shape.equals("status-only") ? null : targetDeployment,
                shape.equals("deployment-only") ? null : shape.equals("mixed") ? protectedStatus : targetStatus);
        var preserved = log(protectedDeployment, protectedStatus);
        var unassociated = log(null, null);
        em.flush();

        logs.deleteByCatalogId(target.getId());
        em.flush();
        em.clear();
        assertThat(logs.existsById(owned.getId())).isFalse();
        assertThat(logs.findAll()).extracting(UnifiedLog::getId)
                .containsExactlyInAnyOrder(preserved.getId(), unassociated.getId());
        // This was the production FK failure: deployment-only logs survived the old query.
        em.createQuery("DELETE FROM DeploymentHistory d WHERE d.catalog.id = :id")
                .setParameter("id", target.getId()).executeUpdate();
        em.flush();
        assertThat(em.find(DeploymentHistory.class, protectedDeployment.getId())).isNotNull();
    }

    @Test void reproducesOldImplicitJoinFailureAndThenRemovesTheBlockedLog() {
        var target = catalog("reproduction");
        var owned = log(deployment(target), null);
        em.flush();
        int legacyDeleted = em.createQuery("DELETE FROM UnifiedLog u WHERE u.deployment.catalog.id = :id "
                + "OR u.applicationStatus.catalog.id = :id").setParameter("id", target.getId()).executeUpdate();
        assertThat(legacyDeleted).isZero();
        assertThat(logs.existsById(owned.getId())).isTrue();
        logs.deleteByCatalogId(target.getId());
        em.flush();
        assertThat(logs.existsById(owned.getId())).isFalse();
    }

    @Test void missingCatalogDoesNotDeleteUnrelatedOrUnassociatedLogs() {
        var protectedCatalog = catalog("keep");
        var preserved = log(deployment(protectedCatalog), null);
        var unassociated = log(null, null);
        em.flush();
        logs.deleteByCatalogId(Long.MAX_VALUE);
        assertThat(logs.findAll()).extracting(UnifiedLog::getId)
                .containsExactlyInAnyOrder(preserved.getId(), unassociated.getId());
    }

    private SoftwareCatalog catalog(String name) {
        var value = SoftwareCatalog.builder().name(name).category("Web Server").description("").summary("").build();
        em.persist(value); return value;
    }
    private DeploymentHistory deployment(SoftwareCatalog catalog) {
        var value = DeploymentHistory.builder().catalog(catalog).deploymentType(DeploymentType.K8S)
                .actionType(ActionType.INSTALL).status("UNINSTALLED").executedAt(LocalDateTime.now()).build();
        em.persist(value); return value;
    }
    private ApplicationStatus status(SoftwareCatalog catalog) {
        var value = ApplicationStatus.builder().catalog(catalog).status("UNINSTALLED").build();
        em.persist(value); return value;
    }
    private UnifiedLog log(DeploymentHistory deployment, ApplicationStatus status) {
        var value = UnifiedLog.builder().deployment(deployment).applicationStatus(status)
                .loggedAt(LocalDateTime.now()).severity(UnifiedLog.LogSeverity.INFO)
                .module(UnifiedLog.LogSourceType.APPLICATION).logMessage("Disposable regression fixture").build();
        em.persist(value); return value;
    }
}
