package m.cmp.appManager.catalog.service;

import m.cmp.appManager.catalog.mapper.SwCatalogMapper;
import m.cmp.appManager.catalog.model.SwCatalog;
import m.cmp.appManager.catalog.model.SwCatalogDetail;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class SwCatalogService {


    @Autowired
    SwCatalogMapper scMapper;


    public List<SwCatalog> getSwCatalogList(){
        return scMapper.selectSwCatalogList();
    }

    public SwCatalog setSwCatalog(SwCatalogDetail swCatalogDetail){
        SwCatalog swCatalog = new SwCatalog();
        if(scMapper.insertSwCatalog(swCatalogDetail)){
            swCatalog = getSwCatalogDetail(scMapper.selectLastInsertId());
        }
        return swCatalog;
    }

    public SwCatalogDetail getSwCatalogDetail(Integer scIdx){
        SwCatalogDetail swCatalogDetail = new SwCatalogDetail();
        swCatalogDetail = scMapper.selectSwCatalogDetail(scIdx);
        if(swCatalogDetail.getScIdx() != null){
            swCatalogDetail.setRelationSwCatalog(scMapper.selectRelationSwCatalogList(swCatalogDetail.getScIdx()));
            //swCatalogDetail.setWorkflows(scMapper.selectRelationSwCatalogList);
        }
        return swCatalogDetail;
    }

    public boolean delSwCatalog(Integer scIdx){
        return scMapper.deleteSwCatalog(scIdx);
    }

    public SwCatalogDetail editSwCatalog(SwCatalogDetail swCatalogDetail){
        if(scMapper.updateSwCatalog(swCatalogDetail)) {
            return getSwCatalogDetail(swCatalogDetail.getScIdx());
        }else{
            return null;
        }
    }


    public boolean addSwCatalogRelation(Integer scIdx, Integer refIdx){
        return scMapper.insertSwCatalogRelationCatalog(scIdx, refIdx);
    }

    public boolean delSwCatalogRelation(Integer scIdx, Integer refIdx){
        return scMapper.deleteSwCatalogRelationCatalog(scIdx, refIdx);
    }

    public boolean addSwCatalogWorkflow(Integer scIdx, Integer refIdx){
        return scMapper.insertSwCatalogRelationWorkflow(scIdx, refIdx);
    }

    public boolean delSwCatalogWorkflow(Integer scIdx, Integer refIdx){
        return scMapper.deleteSwCatalogRelationWorkflow(scIdx, refIdx);
    }


}
