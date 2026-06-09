package ${basePackage}.adapter.in.web.dto;

<#list imports as imp>
import ${imp};
</#list>
<#if dtoClass && useLombok>
import lombok.Data;
</#if>

<#if dtoRecord>
public record ${entityName}Response(
<#list responseFields as f>
    ${f.javaType} ${f.fieldName}<#if f?has_next>,</#if>
</#list>
) {
}
<#else>
<#if useLombok>
@Data
</#if>
public class ${entityName}Response {

<#list responseFields as f>
    private ${f.javaType} ${f.fieldName};
</#list>
<#if !useLombok>

<#list responseFields as f>
    public ${f.javaType} get${f.fieldName?cap_first}() { return ${f.fieldName}; }
    public void set${f.fieldName?cap_first}(${f.javaType} ${f.fieldName}) { this.${f.fieldName} = ${f.fieldName}; }
</#list>
</#if>
}
</#if>
