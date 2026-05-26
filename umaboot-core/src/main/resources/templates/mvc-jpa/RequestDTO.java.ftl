package ${basePackage}.dto;

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
public record ${entityName}RequestDTO(
<#list fields as f>
    <#if !f.primaryKey || !f.autoIncrement>
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
    </#if>
</#list>
) {
}
<#else>
<#if useLombok>
@Data
</#if>
public class ${entityName}RequestDTO {

<#list fields as f>
    <#if !f.primaryKey || !f.autoIncrement>
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

    </#if>
</#list>
<#if !useLombok>
<#list fields as f>
    <#if !f.primaryKey || !f.autoIncrement>
    public ${f.javaType} get${f.fieldName?cap_first}() { return ${f.fieldName}; }
    public void set${f.fieldName?cap_first}(${f.javaType} ${f.fieldName}) { this.${f.fieldName} = ${f.fieldName}; }
    </#if>
</#list>
</#if>
}
</#if>
