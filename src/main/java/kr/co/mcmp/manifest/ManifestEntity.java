package kr.co.mcmp.manifest;

import lombok.*;
import javax.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name="MANIFEST")
public class ManifestEntity {


    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(columnDefinition="INT", name="ID")
    private Integer id;

    @Column(columnDefinition="VARCHAR(200) NOT NULL DEFAULT ''", name="TITLE")
    private String title;

    @Column(columnDefinition="TEXT NOT NULL DEFAULT ''", name="MANIFEST")
    private String manifest;

    @Column(columnDefinition="VARCHAR(15) NOT NULL DEFAULT ''", name="TYPE")
    private String type; // yaml, sh, txt, etc...

    @Column(columnDefinition="VARCHAR(15) NOT NULL DEFAULT ''", name="CATEGORY")
    private String category;

    public ManifestEntity(ManifestDTO mDto){
        if(mDto.getManifestIdx() != null) { this.id = mDto.getManifestIdx(); }
        this.title = mDto.getManifestTitle();
        this.manifest = mDto.getManifestContent();
        this.type = mDto.getManifestType();
        this.category = mDto.getManifestCategory();
    }


}
