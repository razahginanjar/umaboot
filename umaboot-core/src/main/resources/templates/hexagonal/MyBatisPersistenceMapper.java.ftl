package ${basePackage}.adapter.out.persistence.mapper;

import ${basePackage}.adapter.out.persistence.${entityName}PersistenceModel;
import ${basePackage}.domain.model.${entityName};
import org.springframework.stereotype.Component;

@Component
public class ${entityName}PersistenceMapper {

    public ${entityName}PersistenceModel toPersistence(${entityName} domain) {
        if (domain == null) return null;
        ${entityName}PersistenceModel pm = new ${entityName}PersistenceModel();
<#list persistenceFields as f>
        pm.set${f.fieldName?cap_first}(domain.get${f.fieldName?cap_first}());
</#list>
        return pm;
    }

    public ${entityName} toDomain(${entityName}PersistenceModel pm) {
        if (pm == null) return null;
        ${entityName} domain = new ${entityName}();
<#list persistenceFields as f>
        domain.set${f.fieldName?cap_first}(pm.get${f.fieldName?cap_first}());
</#list>
        return domain;
    }
}
