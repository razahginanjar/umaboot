package ${basePackage}.adapter.out.persistence.mapper;

import ${basePackage}.adapter.out.persistence.${entityName}JpaEntity;
import ${basePackage}.domain.model.${entityName};
import org.springframework.stereotype.Component;

@Component
public class ${entityName}PersistenceMapper {

    public ${entityName}JpaEntity toJpa(${entityName} domain) {
        if (domain == null) return null;
        ${entityName}JpaEntity jpa = new ${entityName}JpaEntity();
<#list domainFields as f>
        jpa.set${f.fieldName?cap_first}(domain.get${f.fieldName?cap_first}());
</#list>
        return jpa;
    }

    public ${entityName} toDomain(${entityName}JpaEntity jpa) {
        if (jpa == null) return null;
        ${entityName} domain = new ${entityName}();
<#list domainFields as f>
        domain.set${f.fieldName?cap_first}(jpa.get${f.fieldName?cap_first}());
</#list>
        return domain;
    }
}
