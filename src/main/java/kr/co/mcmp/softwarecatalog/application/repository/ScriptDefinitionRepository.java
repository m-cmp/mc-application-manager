package kr.co.mcmp.softwarecatalog.application.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import kr.co.mcmp.softwarecatalog.SoftwareCatalog;
import kr.co.mcmp.softwarecatalog.application.constants.ScriptType;
import kr.co.mcmp.softwarecatalog.application.model.ScriptDefinition;

@Repository
public interface ScriptDefinitionRepository extends JpaRepository<ScriptDefinition, Long> {
    List<ScriptDefinition> findByCatalog(SoftwareCatalog catalog);
    void deleteByCatalog(SoftwareCatalog catalog);
    Optional<ScriptDefinition> findByCatalogAndScriptType(SoftwareCatalog catalog, ScriptType scriptType);
}