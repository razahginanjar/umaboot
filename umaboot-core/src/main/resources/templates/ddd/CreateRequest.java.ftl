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
public record Create${entityName}Request(
<#list requestFields as f>
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
public class Create${entityName}Request {

<#list requestFields as f>
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
<#list requestFields as f>
    public ${f.javaType} get${f.fieldName?cap_first}() { return ${f.fieldName}; }
    public void set${f.fieldName?cap_first}(${f.javaType} ${f.fieldName}) { this.${f.fieldName} = ${f.fieldName}; }
</#list>
</#if>
}
</#if>
