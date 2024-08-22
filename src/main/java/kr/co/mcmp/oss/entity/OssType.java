package kr.co.mcmp.oss.entity;

import lombok.*;

import javax.persistence.*;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "oss_type")
public class OssType {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        @Column(name = "oss_type_idx")
        private Long ossTypeIdx;

        @Column(name = "oss_type_name", nullable = false)
        private String ossTypeName;

        @Column(name = "oss_type_desc")
        private String ossTypeDesc;
}