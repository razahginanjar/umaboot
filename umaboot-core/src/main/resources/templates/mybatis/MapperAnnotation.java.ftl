package ${basePackage}.mapper;

import ${basePackage}.entity.${entityName};
import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ${entityName}Mapper {

    @Insert({
        "INSERT INTO ${table.name} (",
<#list fields as f><#if !f.primaryKey || !f.autoIncrement>        "${f.columnName}<#sep>,</#sep>",
</#if></#list>        ") VALUES (",
<#list fields as f><#if !f.primaryKey || !f.autoIncrement>        "${'#'}{${f.fieldName}}<#sep>,</#sep>",
</#if></#list>        ")"
    })
    @Options(useGeneratedKeys = true, keyProperty = "${idField}")
    int insert(${entityName} record);

    @Update({
        "UPDATE ${table.name} SET",
<#list fields as f><#if !f.primaryKey>        "${f.columnName} = ${'#'}{${f.fieldName}}<#sep>,</#sep>",
</#if></#list>        "WHERE ${idColumn} = ${'#'}{${idField}}"
    })
    int update(${entityName} record);

    @Delete("DELETE FROM ${table.name} WHERE ${idColumn} = #{id}")
    int deleteById(@Param("id") ${idType} id);

    @Select("SELECT * FROM ${table.name} WHERE ${idColumn} = #{id}")
    @Results(id = "${entityName}Result", value = {
<#list fields as f>
        @Result(property = "${f.fieldName}", column = "${f.columnName}"<#if f.primaryKey>, id = true</#if>)<#sep>,</#sep>
</#list>
    })
    ${entityName} findById(@Param("id") ${idType} id);

    @Select("SELECT * FROM ${table.name} ORDER BY ${idColumn} LIMIT #{limit} OFFSET #{offset}")
    @ResultMap("${entityName}Result")
    List<${entityName}> findAll(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM ${table.name}")
    long count();
}
