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
<#list requestUpdateFields as f>
        ${f.javaType} ${f.fieldName}<#sep>,</#sep>
</#list>
) {}
<#else>
public final class Update${entityName}Command {

<#list requestUpdateFields as f>
    private final ${f.javaType} ${f.fieldName};
</#list>

    public Update${entityName}Command(<#list requestUpdateFields as f>${f.javaType} ${f.fieldName}<#sep>, </#sep></#list>) {
<#list requestUpdateFields as f>
        this.${f.fieldName} = ${f.fieldName};
</#list>
    }

<#list requestUpdateFields as f>
    public ${f.javaType} get${f.fieldName?cap_first}() { return ${f.fieldName}; }
</#list>
}
</#if>
