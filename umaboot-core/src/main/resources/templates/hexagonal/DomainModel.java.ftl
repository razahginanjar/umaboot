package ${basePackage}.domain.model;

<#list imports as imp>
import ${imp};
</#list>
import java.util.*;
<#if useLombok>
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
</#if>

<#if table.comment?has_content>
/** ${table.comment} */
</#if>
<#if useLombok>
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
</#if>
public class ${entityName} {

<#list domainFields as f>
    private ${f.javaType} ${f.fieldName};
</#list>
<#list relationships as r>
    <#if r.type == "ManyToOne" && r.owning>
    private ${r.targetEntity} ${r.fieldName};
    <#elseif r.type == "OneToOne" && r.owning>
    private ${r.targetEntity} ${r.fieldName};
    <#elseif r.type == "OneToMany" && !r.owning>
    private List<${r.targetEntity}> ${r.fieldName} = new ArrayList<>();
    <#elseif r.type == "OneToOne" && !r.owning>
    private ${r.targetEntity} ${r.fieldName};
    <#elseif r.type == "ManyToMany">
    private Set<${r.targetEntity}> ${r.fieldName} = new HashSet<>();
    </#if>
</#list>
<#if !useLombok>

    public ${entityName}() {}

<#list domainFields as f>
    public ${f.javaType} get${f.fieldName?cap_first}() { return ${f.fieldName}; }
    public void set${f.fieldName?cap_first}(${f.javaType} ${f.fieldName}) { this.${f.fieldName} = ${f.fieldName}; }
</#list>
</#if>
}
