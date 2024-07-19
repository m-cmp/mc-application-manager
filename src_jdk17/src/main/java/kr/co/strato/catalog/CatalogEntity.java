package kr.co.strato.catalog;

import lombok.*;

import javax.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name="SOFTWARE_CATALOG")
@ToString(exclude = {"SOFTWARE_CATALOG"})
public class CatalogEntity {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(columnDefinition="INT", name="IDX")
    private Integer id;

    @Column(columnDefinition="VARCHAR(200) NOT NULL DEFAULT ''", name="TITLE")
    private String title;

    @Column(columnDefinition="VARCHAR(5000) NOT NULL DEFAULT ''", name="DESCRIPTION")
    private String description;

    @Column(columnDefinition="VARCHAR(200) NOT NULL DEFAULT ''", name="SUMMARY")
    private String summary;

    @Column(columnDefinition="VARCHAR(200) NOT NULL DEFAULT ''", name="ICON")
    private String icon;

    @Column(columnDefinition="VARCHAR(10) NOT NULL DEFAULT ''", name="CATEGORY")
    private String category;




    /*

    INSERT INTO SOFTWARE_CATALOG(TITLE, DESCRIPPTION, SUMMARY, HOMEPAGE, ICON, CATEGORY)
    VALUES
        ('APACHE TOMCAT', 'The Apache Tomcat® software is an open source implementation of the Jakarta Servlet, Jakarta Pages, Jakarta Expression Language, Jakarta WebSocket, Jakarta Annotations and Jakarta Authentication specifications. These specifications are part of the Jakarta EE platform.\n\nThe Jakarta EE platform is the evolution of the Java EE platform. Tomcat 10 and later implement specifications developed as part of Jakarta EE. Tomcat 9 and earlier implement specifications developed as part of Java EE.\n\nThe Apache Tomcat software is developed in an open and participatory environment and released under the Apache License version 2. The Apache Tomcat project is intended to be a collaboration of the best-of-breed developers from around the world. We invite you to participate in this open development project. To learn more about getting involved, click here.\n\nApache Tomcat software powers numerous large-scale, mission-critical web applications across a diverse range of industries and organizations. Some of these users and their stories are listed on the PoweredBy wiki page.\n\nApache Tomcat, Tomcat, Apache, the Apache feather, and the Apache Tomcat project logo are trademarks of the Apache Software Foundation.', 'open source java web application server', 'https://tomcat.apache.org/res/images/tomcat.png', 'WAS'),
        ('REDIS', 'Redis is an in-memory data store used by millions of developers as a cache, vector database, document database, streaming engine, and message broker. Redis has built-in replication and different levels of on-disk persistence. It supports complex data types (for example, strings, hashes, lists, sets, sorted sets, and JSON), with atomic operations defined on those data types.\n\nYou can install Redis from source, from an executable for your OS, or bundled with Redis Stack and Redis Insight which include popular features and monitoring.', 'in memory db', 'https://redis.io/wp-content/uploads/2024/04/Logotype.svg?auto=webp&quality=85,75&width=120', 'DB'),
        ('NGINX', 'nginx [engine x] is an HTTP and reverse proxy server, a mail proxy server, and a generic TCP/UDP proxy server, originally written by Igor Sysoev. For a long time, it has been running on many heavily loaded Russian sites including Yandex, Mail.Ru, VK, and Rambler. Here are some of the success stories: Dropbox, Netflix, FastMail.FM.\n\nThe sources and documentation are distributed under the 2-clause BSD-like license.\n\nCommercial support is available from F5, Inc.', 'web/proxy server', 'https://nginx.org/nginx.png', 'WEB SERVER'),



     */



}
