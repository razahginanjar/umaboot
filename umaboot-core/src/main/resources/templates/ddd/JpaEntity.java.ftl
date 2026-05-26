package ${basePackage}.infrastructure.persistence;

import ${eeNamespace}.persistence.*;
<#list imports as imp>
import ${imp};
</#list>
<#if useLombok>
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
<#if auditable>
import lombok.EqualsAndHashCode;
</#if>
</#if>
<#if softDelete>
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;
</#if>
<#if auditable>
import ${basePackage}.common.Auditable;
</#if>

/**
 * JPA persistence model for {@code ${table.name}}. Decoupled from the domain
 * aggregate {@link ${basePackage}.domain.${aggregatePackage}.${entityName}};
 * mapping happens in {@code ${entityName}JpaMapper}.
 */
<#if useLombok>
@Data
@NoArgsConstructor
@AllArgsConstructor
<#if auditable>
@EqualsAndHashCode(callSuper = false)
</#if>
</#if>
@Entity
@Table(name = "${table.name}"<#if table.schema?has_content>, schema = "${table.schema}"</#if>)
<#if softDelete>
<#if softDeleteIsTimestamp>
@SQLDelete(sql = "UPDATE ${table.name} SET ${softDeleteColumn} = CURRENT_TIMESTAMP WHERE ${idColumn} = ?")
@Where(clause = "${softDeleteColumn} IS NULL")
<#else>
@SQLDelete(sql = "UPDATE ${table.name} SET ${softDeleteColumn} = TRUE WHERE ${idColumn} = ?")
@Where(clause = "${softDeleteColumn} = FALSE")
</#if>
</#if>
public class ${entityName}JpaEntity<#if auditable> extends Auditable</#if> {

<#list fields as f>
    <#if f.primaryKey>
    @Id
        <#if f.autoIncrement>
    @GeneratedValue(strategy = GenerationType.IDENTITY)
        </#if>
    @Column(name = "${f.columnName}"<#if !f.nullable>, nullable = false</#if>)
    private ${f.javaType} ${f.fieldName};

    <#else>
    @Column(name = "${f.columnName}"<#if !f.nullable>, nullable = false</#if><#if f.size gt 0 && f.javaType == "String">, length = ${f.size}</#if>)
    private ${f.javaType} ${f.fieldName};

    </#if>
</#list>
<#if !useLombok>
    public ${entityName}JpaEntity() {}

<#list fields as f>
    public ${f.javaType} get${f.fieldName?cap_first}() { return ${f.fieldName}; }
    public void set${f.fieldName?cap_first}(${f.javaType} ${f.fieldName}) { this.${f.fieldName} = ${f.fieldName}; }
</#list>
</#if>
}
