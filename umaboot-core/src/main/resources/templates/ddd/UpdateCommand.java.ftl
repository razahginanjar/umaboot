package ${basePackage}.application.${aggregatePackage}.command;

<#list imports as imp>
import ${imp};
</#list>

/**
 * Command — input to {@code update} use case of the {@code ${entityName}}
 * aggregate.
 */
<#if springBoot3>
public record Update${entityName}Command(
<#list fields as f><#if !f.primaryKey>
        ${f.javaType} ${f.fieldName}<#sep>,</#sep>
</#if></#list>
) {}
<#else>
public final class Update${entityName}Command {

<#list fields as f><#if !f.primaryKey>
    private final ${f.javaType} ${f.fieldName};
</#if></#list>

    public Update${entityName}Command(<#list fields as f><#if !f.primaryKey>${f.javaType} ${f.fieldName}<#sep>, </#sep></#if></#list>) {
<#list fields as f><#if !f.primaryKey>
        this.${f.fieldName} = ${f.fieldName};
</#if></#list>
    }

<#list fields as f><#if !f.primaryKey>
    public ${f.javaType} get${f.fieldName?cap_first}() { return ${f.fieldName}; }
</#if></#list>
}
</#if>
