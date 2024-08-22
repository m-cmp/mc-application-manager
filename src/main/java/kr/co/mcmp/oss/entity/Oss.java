package kr.co.mcmp.oss.entity;

import lombok.*;

import javax.persistence.*;

@Entity
@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "oss")
public class Oss {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "oss_idx")
    private Long ossIdx;

    @ManyToOne
    @JoinColumn(name = "oss_type_idx", nullable = false)
    private OssType ossType;

    @Column(name = "oss_name", nullable = false)
    private String ossName;

    @Column(name = "oss_desc")
    private String ossDesc;

    @Column(name = "oss_url", nullable = false)
    private String ossUrl;

    @Column(name = "oss_username")
    private String ossUsername;

    @Column(name = "oss_password")
    private String ossPassword;

}