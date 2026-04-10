package kr.co.mcmp.softwarecatalog.application.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.ScriptFormat;
import kr.co.mcmp.softwarecatalog.application.constants.ScriptType;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "SCRIPT_DEFINITION")
@Getter
@Setter
public class ScriptDefinition {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // 스크립트 정의의 고유 식별자

    @ManyToOne
    @JoinColumn(name = "catalog_id")
    private SoftwareCatalog catalog; // 이 스크립트가 속한 소프트웨어 카탈로그

    @Enumerated(EnumType.STRING)
    @Column(name = "script_type", nullable = false)
    private ScriptType scriptType; // 스크립트의 유형 (예: INSTALL, UNINSTALL, RUN, RESTART, STOP)

    @Column(name = "script_content", columnDefinition = "TEXT")
    private String scriptContent; // 실제 스크립트 내용

    @Enumerated(EnumType.STRING)
    @Column(name = "script_format", nullable = false)
    private ScriptFormat scriptFormat; // 스크립트의 형식 (예: XML, SHELL, GROOVY)

}
