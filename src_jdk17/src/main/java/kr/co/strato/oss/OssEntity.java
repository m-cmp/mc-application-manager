package kr.co.strato.oss;

import javax.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Formula;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name="OSS")
@Entity
@ToString(exclude = {"OSS"})
public class OssEntity {
        // idx, PK
        @Id
        @GeneratedValue(strategy=GenerationType.IDENTITY)
        @Column(columnDefinition="INT", name="IDX")
        private Integer id;

        // oss code
        @Column(columnDefinition="VARCHAR(20) NOT NULL", name="CODE")
        private String code;

        @Column(columnDefinition="VARCHAR(100) NOT NULL", name="name")
        private String name;



}
