package m.cmp.appManager.catalog.mapper;

import m.cmp.appManager.jenkins.pipeline.model.Pipeline;
import m.cmp.appManager.catalog.model.SwCatalog;
import m.cmp.appManager.catalog.model.SwCatalogDetail;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

@Mapper
public interface SwCatalogMapper {

    public Integer selectLastInsertId();

    public List<SwCatalog> selectSwCatalogList();

    public SwCatalogDetail selectSwCatalogDetail(Integer scIdx);

    public boolean insertSwCatalog(SwCatalogDetail swCatalogDetail);

    public boolean deleteSwCatalog(Integer scIdx);

    public boolean updateSwCatalog(SwCatalogDetail swCatalogDetail);


    public List<SwCatalog> selectRelationSwCatalogList(Integer scIdx);

    public List<Pipeline> selectRelationWorkflowList(Integer scIdx);


    public boolean insertSwCatalogRelationCatalog(Integer scIdx, Integer refIdx);

    public boolean deleteSwCatalogRelationCatalog(Integer scIdx, Integer refIdx);

    public boolean insertSwCatalogRelationWorkflow(Integer scIdx, Integer refIdx);

    public boolean deleteSwCatalogRelationWorkflow(Integer scIdx, Integer refIdx);


}
