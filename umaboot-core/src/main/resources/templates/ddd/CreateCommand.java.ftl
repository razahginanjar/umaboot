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
<#list requestFields as f>
        ${f.javaType} ${f.fieldName}<#sep>,</#sep>
</#list>
) {}
<#else>
public final class Create${entityName}Command {

<#list requestFields as f>
    private final ${f.javaType} ${f.fieldName};
</#list>

    public Create${entityName}Command(<#list requestFields as f>${f.javaType} ${f.fieldName}<#sep>, </#sep></#list>) {
<#list requestFields as f>
        this.${f.fieldName} = ${f.fieldName};
</#list>
    }

<#list requestFields as f>
    public ${f.javaType} get${f.fieldName?cap_first}() { return ${f.fieldName}; }
</#list>
}
</#if>
