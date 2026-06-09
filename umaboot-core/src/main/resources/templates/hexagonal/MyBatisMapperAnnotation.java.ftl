package ${basePackage}.adapter.out.persistence;

import org.apache.ibatis.annotations.*;

import java.util.List;

@Mapper
public interface ${entityName}MyBatisMapper {

    @Insert({
        "INSERT INTO ${table.name} (",
<#list insertFields as f>        "${f.columnName}<#sep>,</#sep>",
</#list>        ") VALUES (",
<#list insertFields as f>        "${'#'}{${f.fieldName}}<#sep>,</#sep>",
</#list>        ")"
    })
    @Options(useGeneratedKeys = true, keyProperty = "${idField}")
    int insert(${entityName}PersistenceModel record);

    @Update({
        "UPDATE ${table.name} SET",
<#list sqlUpdateFields as f>        "${f.columnName} = ${'#'}{${f.fieldName}}<#sep>,</#sep>",
</#list>        "WHERE ${idColumn} = ${'#'}{${idField}}"
    })
    int update(${entityName}PersistenceModel record);

    @Delete("DELETE FROM ${table.name} WHERE ${idColumn} = #{id}")
    int deleteById(@Param("id") ${idType} id);

    @Select("SELECT * FROM ${table.name} WHERE ${idColumn} = #{id}")
    @Results(id = "${entityName}PersistenceResult", value = {
<#list persistenceFields as f>
        @Result(property = "${f.fieldName}", column = "${f.columnName}"<#if f.primaryKey>, id = true</#if>)<#sep>,</#sep>
</#list>
    })
    ${entityName}PersistenceModel findById(@Param("id") ${idType} id);

    @Select("SELECT * FROM ${table.name} ORDER BY ${idColumn} LIMIT #{limit} OFFSET #{offset}")
    @ResultMap("${entityName}PersistenceResult")
    List<${entityName}PersistenceModel> findAll(@Param("offset") int offset, @Param("limit") int limit);

    @Select("SELECT COUNT(*) FROM ${table.name}")
    long count();

    @Select("SELECT COUNT(*) FROM ${table.name} WHERE ${idColumn} = #{id}")
    int existsById(@Param("id") ${idType} id);
}
