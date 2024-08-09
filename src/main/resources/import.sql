INSERT INTO SOFTWARE_CATALOG(TITLE, DESCRIPTION, SUMMARY, ICON, CATEGORY)
VALUES
    ('APACHE TOMCAT', 'The Apache Tomcat software is an open source implementation of the Jakarta Servlet, Jakarta Pages, Jakarta Expression Language, Jakarta WebSocket, Jakarta Annotations and Jakarta Authentication specifications. These specifications are part of the Jakarta EE platform.\n\nThe Jakarta EE platform is the evolution of the Java EE platform. Tomcat 10 and later implement specifications developed as part of Jakarta EE. Tomcat 9 and earlier implement specifications developed as part of Java EE.\n\nThe Apache Tomcat software is developed in an open and participatory environment and released under the Apache License version 2. The Apache Tomcat project is intended to be a collaboration of the best-of-breed developers from around the world. We invite you to participate in this open development project. To learn more about getting involved, click here.\n\nApache Tomcat software powers numerous large-scale, mission-critical web applications across a diverse range of industries and organizations. Some of these users and their stories are listed on the PoweredBy wiki page.\n\nApache Tomcat, Tomcat, Apache, the Apache feather, and the Apache Tomcat project logo are trademarks of the Apache Software Foundation.', 'open source java web application server', '/images/tomcat.png', 'WAS');
INSERT INTO SOFTWARE_CATALOG(TITLE, DESCRIPTION, SUMMARY, ICON, CATEGORY)
VALUES
    ('REDIS', 'Redis is an in-memory data store used by millions of developers as a cache, vector database, document database, streaming engine, and message broker. Redis has built-in replication and different levels of on-disk persistence. It supports complex data types (for example, strings, hashes, lists, sets, sorted sets, and JSON), with atomic operations defined on those data types.\n\nYou can install Redis from source, from an executable for your OS, or bundled with Redis Stack and Redis Insight which include popular features and monitoring.', 'in memory db', '/images/redis.png', 'DB');
INSERT INTO SOFTWARE_CATALOG(TITLE, DESCRIPTION, SUMMARY, ICON, CATEGORY)
VALUES
    ('NGINX', 'nginx [engine x] is an HTTP and reverse proxy server, a mail proxy server, and a generic TCP/UDP proxy server, originally written by Igor Sysoev. For a long time, it has been running on many heavily loaded Russian sites including Yandex, Mail.Ru, VK, and Rambler. Here are some of the success stories: Dropbox, Netflix, FastMail.FM.\n\nThe sources and documentation are distributed under the 2-clause BSD-like license.\n\nCommercial support is available from F5, Inc.', 'web/proxy server', '/images/nginx.png', 'WEB SERVER');
INSERT INTO SOFTWARE_CATALOG(TITLE, DESCRIPTION, SUMMARY, ICON, CATEGORY)
VALUES
    ('APACHE', 'The Apache HTTP Server Project is an effort to develop and maintain an open-source HTTP server for modern operating systems including UNIX and Windows. The goal of this project is to provide a secure, efficient and extensible server that provides HTTP services in sync with the current HTTP standards.\n\nThe Apache HTTP Server ("httpd") was launched in 1995 and it has been the most popular web server on the Internet since April 1996. It has celebrated its 25th birthday as a project in February 2020.\n\nThe Apache HTTP Server is a project of The Apache Software Foundation.', 'web server', '/images/apache.png', 'WEB SERVER');
INSERT INTO SOFTWARE_CATALOG(TITLE, DESCRIPTION, SUMMARY, ICON, CATEGORY)
VALUES
    ('NEXUS', 'Build fast with the world''s leading artifact repository manager\n\nCentralize\nGive your teams a single source of truth for every component.\n\nStore\nOptimize build performance and storage costs by caching artifacts.\n\nDevelop\nSupport up to 18 package formats in a single deployment.\n\nScale\nDeploy highly available clusters, edge nodes, and test servers freely without per-node charges.\n', 'repository', '/images/nexus.png', 'WEB SERVER');
INSERT INTO SOFTWARE_CATALOG(TITLE, DESCRIPTION, SUMMARY, ICON, CATEGORY)
VALUES
    ('MARIA DB', 'MariaDB Server is one of the most popular open source relational databases. It''s made by the original developers of MySQL and guaranteed to stay open source. It is part of most cloud offerings and the default in most Linux distributions.\n\nIt is built upon the values of performance, stability, and openness, and MariaDB Foundation ensures contributions will be accepted on technical merit. Recent new functionality includes advanced clustering with Galera Cluster 4, compatibility features with Oracle Database and Temporal Data Tables, allowing one to query the data as it stood at any point in the past.', 'database', '/images/mariadb.png', 'RDBMS');
INSERT INTO SOFTWARE_CATALOG(TITLE, DESCRIPTION, SUMMARY, ICON, CATEGORY)
VALUES
    ('GRAFANA', 'Your observability stack, faster and easier\n\nWith Grafana Cloud, enable observability - from operational dashboards to a fully centralized system and everything in between - without the overhead of building and maintaining your own stack.', 'observability', '/images/grafana.jpg', 'OBSERVABILITY');



INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE)
VALUES
    ((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'APACHE TOMCAT'), 0, 'https://tomcat.apache.org/', '', 'HOMEPAGE');
INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE)
VALUES
    ((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'REDIS'), 0, 'https://redis.io/', '', 'HOMEPAGE');
INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE)
VALUES
    ((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'NGINX'), 0, 'https://nginx.org/en/', '', 'HOMEPAGE');
INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE)
VALUES
    ((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'APACHE'), 0, 'https://httpd.apache.org/', '', 'HOMEPAGE');
INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE)
VALUES
    ((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'NEXUS'), 0, 'https://www.sonatype.com/products/sonatype-nexus-repository', '', 'HOMEPAGE');
INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE)
VALUES
    ((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'MARIA DB'), 0, 'https://mariadb.org/', '', 'HOMEPAGE');
INSERT INTO SOFTWARE_CATALOG_REF(CATALOG_ID, REF_IDX, REF_VALUE, REF_DESC, REF_TYPE)
VALUES
    ((SELECT ID FROM SOFTWARE_CATALOG WHERE TITLE = 'GRAFANA'), 0, 'https://grafana.com/', '', 'HOMEPAGE');


