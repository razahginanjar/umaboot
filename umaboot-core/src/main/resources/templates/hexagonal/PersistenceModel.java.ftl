package ${basePackage}.adapter.out.persistence;

<#list imports as imp>
import ${imp};
</#list>
<#if useLombok>
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
</#if>

/**
 * Plain persistence model for {@code ${table.name}} — used by MyBatis.
 *
 * <p>Decoupled from the domain {@link ${basePackage}.domain.model.${entityName}}.
 * The MyBatis Mapper produces / consumes this model; the
 * {@code ${entityName}PersistenceMapper} translates to/from the domain.</p>
 */
<#if useLombok>
@Data
@NoArgsConstructor
@AllArgsConstructor
</#if>
public class ${entityName}PersistenceModel {

<#list fields as f>
    private ${f.javaType} ${f.fieldName};
</#list>
<#if !useLombok>

    public ${entityName}PersistenceModel() {}

<#list fields as f>
    public ${f.javaType} get${f.fieldName?cap_first}() { return ${f.fieldName}; }
    public void set${f.fieldName?cap_first}(${f.javaType} ${f.fieldName}) { this.${f.fieldName} = ${f.fieldName}; }
</#list>
</#if>
}
