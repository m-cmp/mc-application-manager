-- =============================================
-- MC Application Manager Database Initialization
-- =============================================

-- 1. SOFTWARE_CATALOG 테이블 데이터 삽입
INSERT INTO SOFTWARE_CATALOG (TITLE, DESCRIPTION, SUMMARY, CATEGORY, LOGO_URL_LARGE, LOGO_URL_SMALL, MIN_CPU, RECOMMENDED_CPU, MIN_MEMORY, RECOMMENDED_MEMORY, MIN_DISK, RECOMMENDED_DISK, CPU_THRESHOLD, MEMORY_THRESHOLD, MIN_REPLICAS, MAX_REPLICAS, HPA_ENABLED, DEFAULT_PORT, INGRESS_ENABLED, INGRESS_HOST, INGRESS_PATH, INGRESS_CLASS, INGRESS_TLS_ENABLED, INGRESS_TLS_SECRET, CREATED_AT, UPDATED_AT) VALUES
('Apache Tomcat', 'Apache Tomcat is an open-source implementation of the Java Servlet, JavaServer Pages, Java Expression Language and Java WebSocket technologies.', 'Open-source Java web application server', 'Web Servers', 'https://djeqr6to3dedg.cloudfront.net/repo-logos/library/tomcat/live/logo-1720462300603.png', 'https://djeqr6to3dedg.cloudfront.net/repo-logos/library/tomcat/live/logo-1720462300603.png', 1, 2, 1, 1, 1, 2, 80.0, 80.0, 1, 5, false, 8080, true, 'tomcat.example.com', '/', 'nginx', false, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Redis', 'Redis is an open source, in-memory data structure store, used as a database, cache, and message broker.', 'In-memory data structure store', 'Databases & Storage', 'https://djeqr6to3dedg.cloudfront.net/repo-logos/library/redis/live/logo-1720462263103.png', 'https://djeqr6to3dedg.cloudfront.net/repo-logos/library/redis/live/logo-1720462263103.png', 2, 2, 1, 4, 1, 1, 80.0, 80.0, 1, 5, false, 6379, false, NULL, NULL, NULL, false, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Nginx', 'NGINX is a free, open-source, high-performance HTTP server and reverse proxy, as well as an IMAP/POP3 proxy server.', 'High-performance HTTP server', 'Web Servers', 'https://djeqr6to3dedg.cloudfront.net/repo-logos/library/nginx/live/logo-1720462242584.png', 'https://djeqr6to3dedg.cloudfront.net/repo-logos/library/nginx/live/logo-1720462242584.png', 1, 2, 1, 1, 1, 1, 80.0, 80.0, 1, 5, false, 80, true, 'nginx.example.com', '/', 'nginx', true, 'nginx-tls', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Apache HTTP Server', 'The Apache HTTP Server is a powerful, flexible, HTTP/1.1 compliant web server.', 'Popular web server', 'Web Servers', 'https://www.gravatar.com/avatar/d57617e2eca42ca07dfc380b85585d64?s=80&r=g&d=mm', 'https://www.gravatar.com/avatar/d57617e2eca42ca07dfc380b85585d64?s=80&r=g&d=mm', 1, 2, 1, 1, 1, 1, 80.0, 80.0, 1, 5, false, 80, true, 'apache.example.com', '/', 'nginx', true, 'apache-tls', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Nexus Repository', 'Nexus Repository OSS is an open source repository that supports many artifact formats.', 'Artifact repository manager', 'Databases & Storage', 'https://www.gravatar.com/avatar/614e0f6491dbb293e540190b02b3024e?s=80&r=g&d=mm', 'https://www.gravatar.com/avatar/614e0f6491dbb293e540190b02b3024e?s=80&r=g&d=mm', 4, 8, 4, 8, 2, 4, 80.0, 80.0, 1, 3, true, 8081, true, 'nexus.example.com', '/', 'nginx', true, 'nexus-tls', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('MariaDB', 'MariaDB is a community-developed, commercially supported fork of the MySQL relational database management system.', 'Open-source relational database', 'Databases & Storage', 'https://djeqr6to3dedg.cloudfront.net/repo-logos/library/mariadb/live/logo-1720462226239.png', 'https://djeqr6to3dedg.cloudfront.net/repo-logos/library/mariadb/live/logo-1720462226239.png', 1, 4, 1, 4, 2, 4, 80.0, 80.0, 1, 3, false, 3306, false, NULL, NULL, NULL, false, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Grafana', 'Grafana is an open-source platform for monitoring and observability.', 'Monitoring and visualization platform', 'MONITORING_AND_LOGGING', 'https://desktop.docker.com/extensions/grafana_docker-desktop-extension/storage_googleapis_com/grafanalabs-integration-logos/grafana_icon.svg', 'https://desktop.docker.com/extensions/grafana_docker-desktop-extension/storage_googleapis_com/grafanalabs-integration-logos/grafana_icon.svg', 0.1, 0.2, 0.1, 0.2, 1, 2, 80.0, 80.0, 1, 3, true, 3000, true, 'grafana.example.com', '/', 'nginx', true, 'grafana-tls', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Prometheus', 'Prometheus is an open-source systems monitoring and alerting toolkit.', 'Monitoring and alerting toolkit', 'Integration & Delivery,Monitoring & Observability,Security', 'https://www.gravatar.com/avatar/31cea69afa424609b2d83621b4d47f1d?s=80&r=g&d=mm', 'https://www.gravatar.com/avatar/31cea69afa424609b2d83621b4d47f1d?s=80&r=g&d=mm', 2, 4, 2, 4, 1, 2, 80.0, 80.0, 1, 3, true, 9090, true, 'prometheus.example.com', '/', 'nginx', true, 'prometheus-tls', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('Elasticsearch', 'Elasticsearch is a distributed, RESTful search and analytics engine capable of solving a growing number of use cases.', 'Distributed search and analytics engine', 'Databases & Storage', 'https://www.gravatar.com/avatar/dd9d954997353b37b4c2684f478192d3?s=120&r=g&d=404', 'https://www.gravatar.com/avatar/dd9d954997353b37b4c2684f478192d3?s=120&r=g&d=404', 2, 4, 2, 8, 2, 4, 80.0, 80.0, 1, 5, true, 9200, true, 'elasticsearch.example.com', '/', 'nginx', true, 'elasticsearch-tls', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 2. PACKAGE_INFO 테이블 데이터 삽입 (DockerHub 기반)
INSERT INTO PACKAGE_INFO (CATALOG_ID, PACKAGE_TYPE, PACKAGE_NAME, PACKAGE_VERSION, REPOSITORY_URL, DOCKER_IMAGE_ID, DOCKER_PUBLISHER, DOCKER_CREATED_AT, DOCKER_UPDATED_AT, DOCKER_SHORT_DESCRIPTION, DOCKER_SOURCE, ARCHITECTURES, CATEGORIES, IS_ARCHIVED, IS_AUTOMATED, IS_OFFICIAL, LAST_PULLED_AT, OPERATING_SYSTEMS, PULL_COUNT, STAR_COUNT) VALUES
(1, 'DOCKER', 'tomcat', 'latest', 'https://hub.docker.com/_/tomcat', NULL, 'library', '2014-10-27 17:28:47.715247', '2024-11-16 12:33:35.309136', 'Apache Tomcat is an open source implementation of the Java Servlet and JavaServer Pages technologies', 'store', 'arm,arm64,ppc64le,riscv64,s390x,386,amd64', 'Web Servers', false, false, true, '2024-11-17 00:00:52.66311', 'linux', '500M+', 3703),
(2, 'DOCKER', 'redis', 'latest', 'https://hub.docker.com/_/redis', NULL, 'library', '2014-06-05 20:04:50', '2024-11-13 05:06:01.750358', 'Redis is the world''s fastest data platform for caching, vector search, and NoSQL databases.', 'store', 's390x,arm64,mips64le,arm,ppc64le,riscv64,386,amd64', 'Databases & Storage', false, false, true, '2024-11-16 23:59:52.43899', 'unknown,linux,windows', '1B+', 13089),
(3, 'DOCKER', 'nginx', 'latest', 'https://hub.docker.com/_/nginx', NULL, 'library', '2014-06-05 19:14:45', '2024-11-13 18:54:10.381375', 'Official build of Nginx.', 'store', 'mips64le,ppc64le,s390x,386,amd64,arm,arm64', 'Web Servers', false, false, true, '2024-11-16 23:59:52.411318', 'linux', '1B+', 20371),
(4, 'DOCKER', 'httpd', 'latest', 'https://hub.docker.com/_/httpd', NULL, 'library', '2014-11-03 20:13:35.098911', '2024-11-12 06:53:14.008833', 'The Apache HTTP Server Project', 'store', '386,s390x,arm64,mips64le,ppc64le,riscv64,amd64,arm', 'Web Servers', false, false, true, '2024-11-16 23:59:52.416276', 'linux', '1B+', 4811),
(5, 'DOCKER', 'sonatype/nexus', 'latest', 'https://hub.docker.com/r/sonatype/nexus', NULL, 'sonatype', '2014-11-30 00:48:42.277735', '2024-11-13 23:30:59.187382', 'Sonatype Nexus', 'community', 'amd64', 'Databases & Storage', false, true, false, '2024-11-17 00:00:53.742239', 'linux', '10M+', 454),
(6, 'DOCKER', 'mariadb', 'latest', 'https://hub.docker.com/_/mariadb', NULL, 'library', '2014-11-25 23:08:03.441528', '2024-11-16 07:36:49.596577', 'MariaDB Server is a high performing open source relational database, forked from MySQL.', 'store', 'ppc64le,s390x,386,amd64,arm64', 'Databases & Storage', false, false, true, '2024-11-16 23:59:52.448539', 'unknown,linux', '1B+', 5880),
(7, 'DOCKER', 'grafana/grafana', 'latest', 'https://hub.docker.com/r/grafana/grafana', NULL, 'Grafana Labs', '2015-02-06 09:29:43.405876', '2024-11-16 14:04:58.225375', 'The official Grafana docker container', 'verified_publisher', 'amd64,arm,arm64', 'Internet of Things,Monitoring & Observability,Security', false, false, false, '2024-11-17 00:00:54.128838', 'linux', '1B+', 3210),
(8, 'DOCKER', 'bitnami/prometheus', 'latest', 'https://hub.docker.com/r/bitnami/prometheus', NULL, 'VMware', '2018-04-04 14:36:04.576861', '2024-11-09 23:50:22.097247', 'Bitnami container image for Prometheus', 'verified_publisher', 'amd64,arm64', 'Integration & Delivery,Monitoring & Observability,Security', false, true, false, '2024-11-17 00:14:24.131874', 'linux', '50M+', 235),
(9, 'DOCKER', 'elastic/elasticsearch', '8.11.0', 'https://hub.docker.com/r/elastic/elasticsearch', NULL, 'elastic', '2014-06-05 20:04:50', '2024-11-13 05:06:01.750358', 'Elasticsearch is a distributed, RESTful search and analytics engine', 'verified_publisher', 'amd64,arm64', 'Databases & Storage', false, false, false, '2024-11-17 00:00:52.66311', 'linux', '1B+', 5000);

-- 3. HELM_CHART 테이블 데이터 삽입 (ArtifactHub 기반)
INSERT INTO HELM_CHART (CATALOG_ID, CATEGORY, CHART_NAME, CHART_VERSION, CHART_REPOSITORY_URL, VALUES_FILE, HAS_VALUES_SCHEMA, REPOSITORY_NAME, REPOSITORY_OFFICIAL, REPOSITORY_DISPLAY_NAME, IMAGE_REPOSITORY) VALUES
(7, 'MONITORING_AND_LOGGING', 'grafana', '7.3.0', 'https://grafana.github.io/helm-charts', 'https://artifacthub.io/packages/helm/grafana/grafana/values.yaml', true, 'grafana', true, 'Grafana', 'grafana/grafana'),
(8, 'MONITORING_AND_LOGGING', 'prometheus', '25.8.0', 'https://prometheus-community.github.io/helm-charts', 'https://artifacthub.io/packages/helm/prometheus-community/prometheus/values.yaml', true, 'prometheus-community', true, 'Prometheus Community', 'bitnami/prometheus');

-- 4. SOFTWARE_SOURCE_MAPPING 테이블 데이터 삽입 (서브쿼리 없이 직접 ID 사용)
-- Grafana - DockerHub + ArtifactHub 둘 다 지원
-- INSERT INTO SOFTWARE_SOURCE_MAPPING (CATALOG_ID, SOURCE_TYPE, SOURCE_ID, IS_PRIMARY, PRIORITY) VALUES
-- (7, 'DOCKERHUB', 7, TRUE, 1),
-- (7, 'ARTIFACTHUB', 1, FALSE, 2);

-- -- Prometheus - DockerHub + ArtifactHub 둘 다 지원
-- INSERT INTO SOFTWARE_SOURCE_MAPPING (CATALOG_ID, SOURCE_TYPE, SOURCE_ID, IS_PRIMARY, PRIORITY) VALUES
-- (8, 'DOCKERHUB', 8, TRUE, 1),
-- (8, 'ARTIFACTHUB', 2, FALSE, 2);

-- -- Redis - DockerHub만 지원
-- INSERT INTO SOFTWARE_SOURCE_MAPPING (CATALOG_ID, SOURCE_TYPE, SOURCE_ID, IS_PRIMARY, PRIORITY) VALUES
-- (2, 'DOCKERHUB', 2, TRUE, 1);

-- -- Nginx - DockerHub만 지원
-- INSERT INTO SOFTWARE_SOURCE_MAPPING (CATALOG_ID, SOURCE_TYPE, SOURCE_ID, IS_PRIMARY, PRIORITY) VALUES
-- (3, 'DOCKERHUB', 3, TRUE, 1);

-- -- Apache HTTP Server - DockerHub만 지원
-- INSERT INTO SOFTWARE_SOURCE_MAPPING (CATALOG_ID, SOURCE_TYPE, SOURCE_ID, IS_PRIMARY, PRIORITY) VALUES
-- (4, 'DOCKERHUB', 4, TRUE, 1);

-- -- Nexus Repository - DockerHub만 지원
-- INSERT INTO SOFTWARE_SOURCE_MAPPING (CATALOG_ID, SOURCE_TYPE, SOURCE_ID, IS_PRIMARY, PRIORITY) VALUES
-- (5, 'DOCKERHUB', 5, TRUE, 1);

-- -- MariaDB - DockerHub만 지원
-- INSERT INTO SOFTWARE_SOURCE_MAPPING (CATALOG_ID, SOURCE_TYPE, SOURCE_ID, IS_PRIMARY, PRIORITY) VALUES
-- (6, 'DOCKERHUB', 6, TRUE, 1);

-- 5. SOFTWARE_CATALOG_REF 테이블 데이터 삽입
-- Apache Tomcat
INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE) VALUES
(1, 0, 'https://tomcat.apache.org/', '', 'HOMEPAGE'),
(1, 1, 'apache', '', 'TAG'),
(1, 2, 'oss', '', 'TAG'),
(1, 3, 'server', '', 'TAG'),
(1, 4, 'vm_application_install', '', 'workflow'),
(1, 5, 'vm_application_uninstall', '', 'workflow'),
(1, 6, 'helm_application_install', '', 'workflow'),
(1, 7, 'helm_application_uninstall', '', 'workflow');

-- Redis
INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE) VALUES
(2, 0, 'https://redis.io/', '', 'HOMEPAGE'),
(2, 1, 'NoSQL', '', 'TAG'),
(2, 2, 'oss', '', 'TAG'),
(2, 3, 'inMemoryDB', '', 'TAG'),
(2, 4, 'vm_application_install', '', 'workflow'),
(2, 5, 'vm_application_uninstall', '', 'workflow'),
(2, 6, 'helm_application_install', '', 'workflow'),
(2, 7, 'helm_application_uninstall', '', 'workflow');

-- Nginx
INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE) VALUES
(3, 0, 'https://nginx.org/en/', '', 'HOMEPAGE'),
(3, 1, 'apache', '', 'TAG'),
(3, 2, 'oss', '', 'TAG'),
(3, 3, 'proxy', '', 'TAG'),
(3, 4, 'web', '', 'TAG'),
(3, 5, 'frontend', '', 'TAG'),
(3, 6, 'server', '', 'TAG'),
(3, 7, 'vm_application_install', '', 'workflow'),
(3, 8, 'vm_application_uninstall', '', 'workflow'),
(3, 9, 'helm_application_install', '', 'workflow'),
(3, 10, 'helm_application_uninstall', '', 'workflow');

-- Apache HTTP Server
INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE) VALUES
(4, 0, 'https://httpd.apache.org/', '', 'HOMEPAGE'),
(4, 1, 'web', '', 'TAG'),
(4, 2, 'oss', '', 'TAG'),
(4, 3, 'frontend', '', 'TAG'),
(4, 4, 'webserver', '', 'TAG'),
(4, 5, 'httpd', '', 'TAG'),
(4, 6, 'vm_application_install', '', 'workflow'),
(4, 7, 'vm_application_uninstall', '', 'workflow'),
(4, 8, 'helm_application_install', '', 'workflow'),
(4, 9, 'helm_application_uninstall', '', 'workflow');

-- Nexus Repository
INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE) VALUES
(5, 0, 'https://www.sonatype.com/products/sonatype-nexus-repository', '', 'HOMEPAGE'),
(5, 1, 'repository', '', 'TAG'),
(5, 2, 'oss', '', 'TAG'),
(5, 3, 'license', '', 'TAG'),
(5, 4, 'vm_application_install', '', 'workflow'),
(5, 5, 'vm_application_uninstall', '', 'workflow'),
(5, 6, 'helm_application_install', '', 'workflow'),
(5, 7, 'helm_application_uninstall', '', 'workflow');

-- MariaDB
INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE) VALUES
(6, 0, 'https://mariadb.org/', '', 'HOMEPAGE'),
(6, 1, 'RDBMS', '', 'TAG'),
(6, 2, 'oss', '', 'TAG'),
(6, 3, 'database', '', 'TAG'),
(6, 4, 'vm_application_install', '', 'workflow'),
(6, 5, 'vm_application_uninstall', '', 'workflow'),
(6, 6, 'helm_application_install', '', 'workflow'),
(6, 7, 'helm_application_uninstall', '', 'workflow');

-- Grafana
INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE) VALUES
(7, 0, 'https://grafana.com/', '', 'HOMEPAGE'),
(7, 1, 'view', '', 'TAG'),
(7, 2, 'observer', '', 'TAG'),
(7, 3, 'oss', '', 'TAG'),
(7, 4, 'vm_application_install', '', 'workflow'),
(7, 5, 'vm_application_uninstall', '', 'workflow'),
(7, 6, 'helm_application_install', '', 'workflow'),
(7, 7, 'helm_application_uninstall', '', 'workflow');

-- Prometheus
INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE) VALUES
(8, 0, 'https://prometheus.io/', '', 'HOMEPAGE'),
(8, 1, 'monitoring', '', 'TAG'),
(8, 2, 'metrics', '', 'TAG'),
(8, 3, 'oss', '', 'TAG'),
(8, 4, 'vm_application_install', '', 'workflow'),
(8, 5, 'vm_application_uninstall', '', 'workflow'),
(8, 6, 'helm_application_install', '', 'workflow'),
(8, 7, 'helm_application_uninstall', '', 'workflow');

-- Elasticsearch
INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE) VALUES
(9, 0, 'https://www.elastic.co/elasticsearch/', '', 'HOMEPAGE'),
(9, 1, 'search', '', 'TAG'),
(9, 2, 'analytics', '', 'TAG'),
(9, 3, 'oss', '', 'TAG'),
(9, 4, 'distributed', '', 'TAG'),
(9, 5, 'vm_application_install', '', 'workflow'),
(9, 6, 'vm_application_uninstall', '', 'workflow'),
(9, 7, 'vm_clustering_install', '', 'workflow'),
(9, 8, 'vm_clustering_uninstall', '', 'workflow'),
(9, 9, 'helm_application_install', '', 'workflow'),
(9, 10, 'helm_application_uninstall', '', 'workflow');

-- 6. OSS_TYPE 테이블 데이터 삽입
INSERT INTO oss_type (oss_type_idx, oss_type_name, oss_type_desc) VALUES
(1, 'NEXUS', 'Application Repository');
-- (2, 'JENKINS', 'Application Provisioning Engine');

-- 7. OSS 테이블 데이터 삽입
INSERT INTO oss (oss_idx, oss_type_idx, oss_name, oss_desc, oss_url, oss_username, oss_password) VALUES
(1, 1, 'NEXUS', 'Application Repository', 'http://mc-application-manager-sonatype-nexus:8081', 'admin', '7TEWaICzct4JsjFGMYtgaA==');
-- (2, 2, 'Ape', 'Application Provisioning Engine', 'http://mc-application-manager-jenkins:8080', 'admin', '7TEWaICzct4JsjFGMYtgaA==');