package ${basePackage}.infrastructure.persistence;

<#list imports as imp>
import ${imp};
</#list>
<#if useLombok>
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
</#if>

/**
 * Plain persistence model for {@code ${table.name}} (MyBatis-backed).
 * Decoupled from the domain aggregate {@link ${basePackage}.domain.${aggregatePackage}.${entityName}}.
 */
<#if useLombok>
@Data
@NoArgsConstructor
@AllArgsConstructor
</#if>
public class ${entityName}PersistenceModel {

<#list persistenceFields as f>
    private ${f.javaType} ${f.fieldName};
</#list>
<#if !useLombok>

    public ${entityName}PersistenceModel() {}

<#list persistenceFields as f>
    public ${f.javaType} get${f.fieldName?cap_first}() { return ${f.fieldName}; }
    public void set${f.fieldName?cap_first}(${f.javaType} ${f.fieldName}) { this.${f.fieldName} = ${f.fieldName}; }
</#list>
</#if>
}
