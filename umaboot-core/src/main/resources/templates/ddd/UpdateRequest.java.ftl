package ${basePackage}.interfaces.rest.dto;

<#if validationJakarta>
import ${eeNamespace}.validation.constraints.*;
</#if>
<#list imports as imp>
import ${imp};
</#list>
<#if dtoClass && useLombok>
import lombok.Data;
</#if>

<#if dtoRecord>
public record Update${entityName}Request(
<#list requestUpdateFields as f>
        <#if validationJakarta && !f.nullable>
            <#if f.javaType == "String">
        @NotBlank
                <#if f.size gt 0>
        @Size(max = ${f.size})
                </#if>
            <#else>
        @NotNull
            </#if>
        </#if>
        ${f.javaType} ${f.fieldName}<#if f?has_next>,</#if>
</#list>
) {
}
<#else>
<#if useLombok>
@Data
</#if>
public class Update${entityName}Request {

<#list requestUpdateFields as f>
        <#if validationJakarta && !f.nullable>
            <#if f.javaType == "String">
    @NotBlank
                <#if f.size gt 0>
    @Size(max = ${f.size})
                </#if>
            <#else>
    @NotNull
            </#if>
        </#if>
    private ${f.javaType} ${f.fieldName};

</#list>
<#if !useLombok>
<#list requestUpdateFields as f>
    public ${f.javaType} get${f.fieldName?cap_first}() { return ${f.fieldName}; }
    public void set${f.fieldName?cap_first}(${f.javaType} ${f.fieldName}) { this.${f.fieldName} = ${f.fieldName}; }
</#list>
</#if>
}
</#if>
