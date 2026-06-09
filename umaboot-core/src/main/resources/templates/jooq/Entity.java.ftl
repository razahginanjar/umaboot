package ${basePackage}.entity;

<#list imports as imp>
import ${imp};
</#list>
<#if useLombok>
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
</#if>

<#if useLombok>
@Data
@NoArgsConstructor
@AllArgsConstructor
</#if>
public class ${entityName} {

<#list entityFields as f>
    private ${f.javaType} ${f.fieldName};
</#list>
<#if !useLombok>

<#list entityFields as f>
    public ${f.javaType} get${f.fieldName?cap_first}() { return ${f.fieldName}; }
    public void set${f.fieldName?cap_first}(${f.javaType} ${f.fieldName}) { this.${f.fieldName} = ${f.fieldName}; }
</#list>
</#if>
}
