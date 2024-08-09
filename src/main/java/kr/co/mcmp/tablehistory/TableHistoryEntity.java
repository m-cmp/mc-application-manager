package kr.co.mcmp.tablehistory;

import lombok.*;

import javax.persistence.*;

@Entity
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name="ACTION_HISTORY")
//@ToString(exclude = {"TABLE_HISTORY"})
public class TableHistoryEntity {

    @Id
    @GeneratedValue(strategy= GenerationType.IDENTITY)
    @Column(columnDefinition="INT", name="ID")
    private Integer id;

    @Column(columnDefinition="VARCHAR(30) NOT NULL", name="TABLENAME")
    private String tablename;

    @Column(columnDefinition="VARCHAR(10) NOT NULL", name="ACTION")
    private String action;

    @Column(columnDefinition="DATETIME NOT NULL DEFAULT NOW()", name="ACTION_DATE")
    private String actionDate;

    @Column(columnDefinition="VARCHAR(50)", name="USER")
    private String user;

    @Builder
    public TableHistoryEntity(TableHistoryDto dto){
        this.tablename = dto.getTablename();
        this.action = dto.getAction();
        this.actionDate = dto.getActionDate();
        this.user = dto.getUser();
    }

    public TableHistoryDto toDto(){
        return new TableHistoryDto(tablename, action, actionDate, user);
    }


}
