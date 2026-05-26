package ${basePackage}.application.${aggregatePackage}.command;

<#list imports as imp>
import ${imp};
</#list>

/**
 * Command — input to {@code create} use case of the {@code ${entityName}}
 * aggregate. Constructed from the REST request DTO by the inbound web mapper.
 */
<#if springBoot3>
public record Create${entityName}Command(
<#list fields as f><#if !f.primaryKey || !f.autoIncrement>
        ${f.javaType} ${f.fieldName}<#sep>,</#sep>
</#if></#list>
) {}
<#else>
public final class Create${entityName}Command {

<#list fields as f><#if !f.primaryKey || !f.autoIncrement>
    private final ${f.javaType} ${f.fieldName};
</#if></#list>

    public Create${entityName}Command(<#list fields as f><#if !f.primaryKey || !f.autoIncrement>${f.javaType} ${f.fieldName}<#sep>, </#sep></#if></#list>) {
<#list fields as f><#if !f.primaryKey || !f.autoIncrement>
        this.${f.fieldName} = ${f.fieldName};
</#if></#list>
    }

<#list fields as f><#if !f.primaryKey || !f.autoIncrement>
    public ${f.javaType} get${f.fieldName?cap_first}() { return ${f.fieldName}; }
</#if></#list>
}
</#if>
