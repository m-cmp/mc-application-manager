package kr.co.mcmp.softwarecatalog;

import javax.persistence.*;

import lombok.*;

@Entity
@Table(name = "PORT_MAPPING")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PortMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ID")
    private Long id;

    @Column(name = "TARGET_PORT", nullable = false)
    private Integer targetPort;

    @Column(name = "HOST_PORT")
    private Integer hostPort;

    @Column(name = "PROTOCOL", length = 10)
    private String protocol; // TCP, UDP, SCTP

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "CATALOG_ID")
    private SoftwareCatalog catalog;
}
