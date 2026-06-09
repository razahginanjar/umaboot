<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="${basePackage}.infrastructure.persistence.${entityName}MyBatisMapper">

    <resultMap id="${entityName}ResultMap"
               type="${basePackage}.infrastructure.persistence.${entityName}PersistenceModel">
<#list persistenceFields as f>
        <#if f.primaryKey>
        <id property="${f.fieldName}" column="${f.columnName}"/>
        <#else>
        <result property="${f.fieldName}" column="${f.columnName}"/>
        </#if>
</#list>
    </resultMap>

    <sql id="columns">
<#list persistenceFields as f>${f.columnName}<#sep>, </#sep></#list>
    </sql>

    <insert id="insert" parameterType="${basePackage}.infrastructure.persistence.${entityName}PersistenceModel"
            useGeneratedKeys="true" keyProperty="${idField}">
        INSERT INTO ${table.name}
        (<#list insertFields as f>${f.columnName}<#sep>, </#sep></#list>)
        VALUES
        (<#list insertFields as f>${'#'}{${f.fieldName}}<#sep>, </#sep></#list>)
    </insert>

    <update id="update" parameterType="${basePackage}.infrastructure.persistence.${entityName}PersistenceModel">
        UPDATE ${table.name}
        SET
<#list sqlUpdateFields as f>            ${f.columnName} = ${'#'}{${f.fieldName}}<#sep>,
</#sep></#list>

        WHERE ${idColumn} = ${'#'}{${idField}}
    </update>

    <delete id="deleteById">
        DELETE FROM ${table.name} WHERE ${idColumn} = #{id}
    </delete>

    <select id="findById" resultMap="${entityName}ResultMap">
        SELECT <include refid="columns"/>
        FROM ${table.name}
        WHERE ${idColumn} = #{id}
    </select>

    <select id="findAll" resultMap="${entityName}ResultMap">
        SELECT <include refid="columns"/>
        FROM ${table.name}
        ORDER BY ${idColumn}
        LIMIT #{limit} OFFSET #{offset}
    </select>

    <select id="count" resultType="long">
        SELECT COUNT(*) FROM ${table.name}
    </select>

    <select id="existsById" resultType="int">
        SELECT COUNT(*) FROM ${table.name} WHERE ${idColumn} = #{id}
    </select>
</mapper>
