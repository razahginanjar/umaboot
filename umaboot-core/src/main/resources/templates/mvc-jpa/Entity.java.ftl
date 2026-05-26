package ${basePackage}.entity;

import ${eeNamespace}.persistence.*;
<#list imports as imp>
import ${imp};
</#list>
<#if useLombok>
import lombok.AllArgsConstructor;
import lombok.Data;
<#if !auditable>
import lombok.NoArgsConstructor;
</#if>
<#if auditable>
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
</#if>
</#if>
<#if softDelete>
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.Where;
</#if>
<#if auditable>
import ${basePackage}.common.Auditable;
</#if>
import java.util.*;

<#if table.comment?has_content>
/** ${table.comment} */
</#if>
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
public class ${entityName}<#if auditable> extends Auditable</#if> {

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
<#list relationships as r>
    <#if r.type == "ManyToOne" && r.owning>
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "${r.fromColumns[0]}"<#if r.fromColumns?size == 1>, referencedColumnName = "${r.toColumns[0]}"</#if>)
    private ${r.targetEntity} ${r.fieldName};

    <#elseif r.type == "OneToOne" && r.owning>
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "${r.fromColumns[0]}", referencedColumnName = "${r.toColumns[0]}")
    private ${r.targetEntity} ${r.fieldName};

    <#elseif r.type == "OneToMany" && !r.owning>
    @OneToMany(mappedBy = "${r.mappedBy}", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<${r.targetEntity}> ${r.fieldName} = new ArrayList<>();

    <#elseif r.type == "OneToOne" && !r.owning>
    @OneToOne(mappedBy = "${r.mappedBy}", cascade = CascadeType.ALL)
    private ${r.targetEntity} ${r.fieldName};

    <#elseif r.type == "ManyToMany">
        <#if r.owning>
    @ManyToMany
    @JoinTable(name = "${r.junctionTable}",
        joinColumns = @JoinColumn(name = "${r.fromColumns[0]}"),
        inverseJoinColumns = @JoinColumn(name = "${r.toColumns[0]}"))
    private Set<${r.targetEntity}> ${r.fieldName} = new HashSet<>();

        <#else>
    @ManyToMany(mappedBy = "${r.fieldName}")
    private Set<${r.targetEntity}> ${r.fieldName} = new HashSet<>();

        </#if>
    </#if>
</#list>
<#if !useLombok>
    public ${entityName}() {}

    public ${idType} get${idField?cap_first}() { return ${idField}; }
    public void set${idField?cap_first}(${idType} ${idField}) { this.${idField} = ${idField}; }
<#list fields as f>
    <#if !f.primaryKey>
    public ${f.javaType} get${f.fieldName?cap_first}() { return ${f.fieldName}; }
    public void set${f.fieldName?cap_first}(${f.javaType} ${f.fieldName}) { this.${f.fieldName} = ${f.fieldName}; }
    </#if>
</#list>
</#if>
}
