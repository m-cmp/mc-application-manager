
INSERT INTO SOFTWARE_CATALOG (TITLE, DESCRIPTION, SUMMARY, CATEGORY, SOURCE_TYPE, LOGO_URL_LARGE, LOGO_URL_SMALL, MIN_CPU, RECOMMENDED_CPU, MIN_MEMORY, RECOMMENDED_MEMORY, MIN_DISK, RECOMMENDED_DISK, CPU_THRESHOLD, MEMORY_THRESHOLD, MIN_REPLICAS, MAX_REPLICAS, HPA_ENABLED, DEFAULT_PORT, CREATED_AT, UPDATED_AT) VALUES
('Apache Tomcat', 'Apache Tomcat is an open-source implementation of the Java Servlet, JavaServer Pages, Java Expression Language and Java WebSocket technologies.', 'Open-source Java web application server', 'WAS', 'DOCKERHUB', 'https://djeqr6to3dedg.cloudfront.net/repo-logos/library/tomcat/live/logo-1720462300603.png', 'https://djeqr6to3dedg.cloudfront.net/repo-logos/library/tomcat/live/logo-1720462300603.png', 1, 2, 256, 512, 1, 2, 80.0, 80.0, 1, 5, false, 8080, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Redis', 'Redis is an open source, in-memory data structure store, used as a database, cache, and message broker.', 'In-memory data structure store', 'DB', 'DOCKERHUB', 'https://djeqr6to3dedg.cloudfront.net/repo-logos/library/redis/live/logo-1720462263103.png', 'https://djeqr6to3dedg.cloudfront.net/repo-logos/library/redis/live/logo-1720462263103.png', 2, 2, 512, 4096, 0.5, 1, 80.0, 80.0, 1, 5, false, 6379, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Nginx', 'NGINX is a free, open-source, high-performance HTTP server and reverse proxy, as well as an IMAP/POP3 proxy server.', 'High-performance HTTP server', 'WEB SERVER', 'DOCKERHUB', 'https://djeqr6to3dedg.cloudfront.net/repo-logos/library/nginx/live/logo-1720462242584.png', 'https://djeqr6to3dedg.cloudfront.net/repo-logos/library/nginx/live/logo-1720462242584.png', 1, 2, 128, 256, 0.5, 1, 80.0, 80.0, 1, 5, false, 80, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Apache HTTP Server', 'The Apache HTTP Server is a powerful, flexible, HTTP/1.1 compliant web server.', 'Popular web server', 'WEB SERVER', 'DOCKERHUB', 'https://www.gravatar.com/avatar/d57617e2eca42ca07dfc380b85585d64?s=80&r=g&d=mm', 'https://www.gravatar.com/avatar/d57617e2eca42ca07dfc380b85585d64?s=80&r=g&d=mm', 1, 2, 256, 512, 0.5, 1, 80.0, 80.0, 1, 5, false, 80, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Nexus Repository', 'Nexus Repository OSS is an open source repository that supports many artifact formats.', 'Artifact repository manager', 'REPOSITORY', 'DOCKERHUB', 'https://www.gravatar.com/avatar/614e0f6491dbb293e540190b02b3024e?s=80&r=g&d=mm', 'https://www.gravatar.com/avatar/614e0f6491dbb293e540190b02b3024e?s=80&r=g&d=mm', 4, 8, 4096, 8192, 2, 4, 80.0, 80.0, 1, 3, true, 8081, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('MariaDB', 'MariaDB is a community-developed, commercially supported fork of the MySQL relational database management system.', 'Open-source relational database', 'RDBMS', 'DOCKERHUB', 'https://djeqr6to3dedg.cloudfront.net/repo-logos/library/mariadb/live/logo-1720462226239.png', 'https://djeqr6to3dedg.cloudfront.net/repo-logos/library/mariadb/live/logo-1720462226239.png', 1, 4, 1024, 4096, 2, 4, 80.0, 80.0, 1, 3, false, 3306, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Grafana', 'Grafana is an open-source platform for monitoring and observability.', 'Monitoring and visualization platform', 'OBSERVABILITY', 'ARTIFACTHUB', 'https://desktop.docker.com/extensions/grafana_docker-desktop-extension/storage_googleapis_com/grafanalabs-integration-logos/grafana_icon.svg', 'https://desktop.docker.com/extensions/grafana_docker-desktop-extension/storage_googleapis_com/grafanalabs-integration-logos/grafana_icon.svg', 2, 4, 2048, 4096, 1, 2, 80.0, 80.0, 1, 3, true, 3000, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Prometheus', 'Prometheus is an open-source systems monitoring and alerting toolkit.', 'Monitoring and alerting toolkit', 'MONITORING', 'DOCKERHUB', 'https://www.gravatar.com/avatar/31cea69afa424609b2d83621b4d47f1d?s=80&r=g&d=mm', 'https://www.gravatar.com/avatar/31cea69afa424609b2d83621b4d47f1d?s=80&r=g&d=mm', 2, 4, 2048, 4096, 1, 2, 80.0, 80.0, 1, 3, true, 9090, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- PackageInfo 데이터 (DockerHub 기반)
INSERT INTO PACKAGE_INFO (
    CATALOG_ID, 
    PACKAGE_TYPE, 
    PACKAGE_NAME, 
    PACKAGE_VERSION, 
    REPOSITORY_URL, 
    DOCKER_IMAGE_ID, 
    DOCKER_PUBLISHER, 
    DOCKER_CREATED_AT, 
    DOCKER_UPDATED_AT, 
    DOCKER_SHORT_DESCRIPTION, 
    DOCKER_SOURCE,
    ARCHITECTURES,
    CATEGORIES,
    IS_ARCHIVED,
    IS_AUTOMATED,
    IS_OFFICIAL,
    LAST_PULLED_AT,
    OPERATING_SYSTEMS,
    PULL_COUNT,
    STAR_COUNT
) VALUES
((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'Apache Tomcat'),
 'DOCKER', 'tomcat', 'latest', 'https://hub.docker.com/_/tomcat',
 NULL, 'library', '2014-10-27 17:28:47.715247', '2024-11-16 12:33:35.309136',
 'Apache Tomcat is an open source implementation of the Java Servlet and JavaServer Pages technologies',
 'store',
 'arm,arm64,ppc64le,riscv64,s390x,386,amd64',
 'Web Servers',
 false, false, true,
 '2024-11-17 00:00:52.66311',
 'linux',
 '500M+', 3703),

((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'Redis'),
 'DOCKER', 'redis', 'latest', 'https://hub.docker.com/_/redis',
 NULL, 'library', '2014-06-05 20:04:50', '2024-11-13 05:06:01.750358',
 'Redis is the world''s fastest data platform for caching, vector search, and NoSQL databases.',
 'store',
 's390x,arm64,mips64le,arm,ppc64le,riscv64,386,amd64',
 'Databases & Storage',
 false, false, true,
 '2024-11-16 23:59:52.43899',
 'unknown,linux,windows',
 '1B+', 13089),

((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'Nginx'),
 'DOCKER', 'nginx', 'latest', 'https://hub.docker.com/_/nginx',
 NULL, 'library', '2014-06-05 19:14:45', '2024-11-13 18:54:10.381375',
 'Official build of Nginx.',
 'store',
 'mips64le,ppc64le,s390x,386,amd64,arm,arm64',
 'Web Servers',
 false, false, true,
 '2024-11-16 23:59:52.411318',
 'linux',
 '1B+', 20371),

((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'Apache HTTP Server'),
 'DOCKER', 'httpd', 'latest', 'https://hub.docker.com/_/httpd',
 NULL, 'library', '2014-11-03 20:13:35.098911', '2024-11-12 06:53:14.008833',
 'The Apache HTTP Server Project',
 'store',
 '386,s390x,arm64,mips64le,ppc64le,riscv64,amd64,arm',
 'Web Servers',
 false, false, true,
 '2024-11-16 23:59:52.416276',
 'linux',
 '1B+', 4811),

((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'Nexus Repository'),
 'DOCKER', 'sonatype/nexus', 'latest', 'https://hub.docker.com/r/sonatype/nexus',
 NULL, 'sonatype', '2014-11-30 00:48:42.277735', '2024-11-13 23:30:59.187382',
 'Sonatype Nexus',
 'community',
 'amd64',
 NULL,
 false, true, false,
 '2024-11-17 00:00:53.742239',
 'linux',
 '10M+', 454),

((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'MariaDB'),
 'DOCKER', 'mariadb', 'latest', 'https://hub.docker.com/_/mariadb',
 NULL, 'library', '2014-11-25 23:08:03.441528', '2024-11-16 07:36:49.596577',
 'MariaDB Server is a high performing open source relational database, forked from MySQL.',
 'store',
 'ppc64le,s390x,386,amd64,arm64',
 'Databases & Storage',
 false, false, true,
 '2024-11-16 23:59:52.448539',
 'unknown,linux',
 '1B+', 5880),

((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'Grafana'),
 'DOCKER', 'grafana/grafana', 'latest', 'https://hub.docker.com/r/grafana/grafana',
 NULL, 'Grafana Labs', '2015-02-06 09:29:43.405876', '2024-11-16 14:04:58.225375',
 'The official Grafana docker container',
 'verified_publisher',
 'amd64,arm,arm64',
 'Internet of Things,Monitoring & Observability,Security',
 false, false, false,
 '2024-11-17 00:00:54.128838',
 'linux',
 '1B+', 3210),

((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'Prometheus'),
 'DOCKER', 'bitnami/prometheus', 'latest', 'https://hub.docker.com/r/bitnami/prometheus',
 NULL, 'VMware', '2018-04-04 14:36:04.576861', '2024-11-09 23:50:22.097247',
 'Bitnami container image for Prometheus',
 'verified_publisher',
 'amd64,arm64',
 'Integration & Delivery,Monitoring & Observability,Security',
 false, true, false,
 '2024-11-17 00:14:24.131874',
 'linux',
 '50M+', 235);

-- HelmChart 데이터 (ArtifactHub 기반)
INSERT INTO HELM_CHART (CATALOG_ID, CHART_NAME, CHART_VERSION, CHART_REPOSITORY_URL, VALUES_FILE, HAS_VALUES_SCHEMA, REPOSITORY_NAME, REPOSITORY_OFFICIAL, REPOSITORY_DISPLAY_NAME , IMAGE_REPOSITORY ) VALUES
((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'Grafana'), 'grafana', '6.59.0', 'https://grafana.github.io/helm-charts', 'https://artifacthub.io/packages/helm/grafana/grafana/values.yaml', true, 'grafana', true, 'Grafana', 'docker.io/grafana/grafana');

-- SOFTWARE_CATALOG_REF 데이터
INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE)
SELECT SC.ID, 0, 'https://tomcat.apache.org/', '', 'HOMEPAGE'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Apache Tomcat'
UNION ALL
SELECT SC.ID, 1, 'apache', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Apache Tomcat'
UNION ALL
SELECT SC.ID, 2, 'oss', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Apache Tomcat'
UNION ALL
SELECT SC.ID, 3, 'server', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Apache Tomcat'
UNION ALL
SELECT SC.ID, 4, 'vm_application_install', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Apache Tomcat'
UNION ALL
SELECT SC.ID, 5, 'vm_application_uninstall', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Apache Tomcat'
UNION ALL
SELECT SC.ID, 6, 'helm_application_install', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Apache Tomcat'
UNION ALL
SELECT SC.ID, 7, 'helm_application_uninstall', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Apache Tomcat';

INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE)
SELECT SC.ID, 0, 'https://redis.io/', '', 'HOMEPAGE'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Redis'
UNION ALL
SELECT SC.ID, 1, 'NoSQL', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Redis'
UNION ALL
SELECT SC.ID, 2, 'oss', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Redis'
UNION ALL
SELECT SC.ID, 3, 'inMemoryDB', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Redis'
UNION ALL
SELECT SC.ID, 4, 'vm_application_install', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Redis'
UNION ALL
SELECT SC.ID, 5, 'vm_application_uninstall', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Redis'
UNION ALL
SELECT SC.ID, 6, 'helm_application_install', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Redis'
UNION ALL
SELECT SC.ID, 7, 'helm_application_uninstall', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Redis';

INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE)
SELECT SC.ID, 0, 'https://nginx.org/en/', '', 'HOMEPAGE'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Nginx'
UNION ALL
SELECT SC.ID, 1, 'apache', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Nginx'
UNION ALL
SELECT SC.ID, 2, 'oss', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Nginx'
UNION ALL
SELECT SC.ID, 3, 'proxy', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Nginx'
UNION ALL
SELECT SC.ID, 4, 'web', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Nginx'
UNION ALL
SELECT SC.ID, 5, 'frontend', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Nginx'
UNION ALL
SELECT SC.ID, 6, 'server', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Nginx'
UNION ALL
SELECT SC.ID, 7, 'vm_application_install', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Nginx'
UNION ALL
SELECT SC.ID, 8, 'vm_application_uninstall', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Nginx'
UNION ALL
SELECT SC.ID, 9, 'helm_application_install', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Nginx'
UNION ALL
SELECT SC.ID, 10, 'helm_application_uninstall', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Nginx';

INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE)
SELECT SC.ID, 0, 'https://httpd.apache.org/', '', 'HOMEPAGE'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Apache HTTP Server'
UNION ALL
SELECT SC.ID, 1, 'web', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Apache HTTP Server'
UNION ALL
SELECT SC.ID, 2, 'oss', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Apache HTTP Server'
UNION ALL
SELECT SC.ID, 3, 'frontend', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Apache HTTP Server'
UNION ALL
SELECT SC.ID, 4, 'webserver', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Apache HTTP Server'
UNION ALL
SELECT SC.ID, 5, 'httpd', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Apache HTTP Server'
UNION ALL
SELECT SC.ID, 6, 'vm_application_install', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Apache HTTP Server'
UNION ALL
SELECT SC.ID, 7, 'vm_application_uninstall', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Apache HTTP Server'
UNION ALL
SELECT SC.ID, 8, 'helm_application_install', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Apache HTTP Server'
UNION ALL
SELECT SC.ID, 9, 'helm_application_uninstall', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Apache HTTP Server';

INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE)
SELECT SC.ID, 0, 'https://www.sonatype.com/products/sonatype-nexus-repository', '', 'HOMEPAGE'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Nexus Repository'
UNION ALL
SELECT SC.ID, 1, 'repository', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Nexus Repository'
UNION ALL
SELECT SC.ID, 2, 'oss', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Nexus Repository'
UNION ALL
SELECT SC.ID, 3, 'license', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Nexus Repository'
UNION ALL
SELECT SC.ID, 4, 'vm_application_install', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Nexus Repository'
UNION ALL
SELECT SC.ID, 5, 'vm_application_uninstall', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Nexus Repository'
UNION ALL
SELECT SC.ID, 6, 'helm_application_install', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Nexus Repository'
UNION ALL
SELECT SC.ID, 7, 'helm_application_uninstall', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Nexus Repository';

INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE)
SELECT SC.ID, 0, 'https://mariadb.org/', '', 'HOMEPAGE'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'MariaDB'
UNION ALL
SELECT SC.ID, 1, 'RDBMS', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'MariaDB'
UNION ALL
SELECT SC.ID, 2, 'oss', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'MariaDB'
UNION ALL
SELECT SC.ID, 3, 'database', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'MariaDB'
UNION ALL
SELECT SC.ID, 4, 'vm_application_install', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'MariaDB'
UNION ALL
SELECT SC.ID, 5, 'vm_application_uninstall', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'MariaDB'
UNION ALL
SELECT SC.ID, 6, 'helm_application_install', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'MariaDB'
UNION ALL
SELECT SC.ID, 7, 'helm_application_uninstall', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'MariaDB';

INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE)
SELECT SC.ID, 0, 'https://grafana.com/', '', 'HOMEPAGE'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Grafana'
UNION ALL
SELECT SC.ID, 1, 'view', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Grafana'
UNION ALL
SELECT SC.ID, 2, 'observer', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Grafana'
UNION ALL
SELECT SC.ID, 3, 'oss', '', 'TAG'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Grafana'
UNION ALL
SELECT SC.ID, 4, 'vm_application_install', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Grafana'
UNION ALL
SELECT SC.ID, 5, 'vm_application_uninstall', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Grafana'
UNION ALL
SELECT SC.ID, 6, 'helm_application_install', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Grafana'
UNION ALL
SELECT SC.ID, 7, 'helm_application_uninstall', '', 'workflow'
FROM SOFTWARE_CATALOG SC WHERE SC.TITLE = 'Grafana';


-- Insert into oss_type
INSERT INTO oss_type (oss_type_idx, oss_type_name, oss_type_desc)
VALUES
    (1, 'NEXUS', 'Application Repository');
INSERT INTO oss_type (oss_type_idx, oss_type_name, oss_type_desc)
VALUES
    (2, 'JENKINS', 'Application Provisioning Engine');

-- Insert into oss
INSERT INTO oss (oss_idx, oss_type_idx, oss_name, oss_desc, oss_url, oss_username, oss_password)
VALUES
    (1, 1, 'NEXUS', 'Application Repository', 'http://mc-application-manager-sonatype-nexus:8081', 'admin', 'cZr3xQjjYfwMQNBO/ebJbQ==');
INSERT INTO oss (oss_idx, oss_type_idx, oss_name, oss_desc, oss_url, oss_username, oss_password)
VALUES
    (2, 2, 'Ape', 'Application Provisioning Engine', 'http://mc-application-manager-jenkins:8080', 'admin', 'cZr3xQjjYfwMQNBO/ebJbQ==');

