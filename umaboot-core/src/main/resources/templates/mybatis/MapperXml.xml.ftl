<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE mapper PUBLIC "-//mybatis.org//DTD Mapper 3.0//EN"
        "https://mybatis.org/dtd/mybatis-3-mapper.dtd">
<mapper namespace="${basePackage}.mapper.${entityName}Mapper">

    <resultMap id="${entityName}ResultMap" type="${basePackage}.entity.${entityName}">
<#list fields as f>
        <#if f.primaryKey>
        <id property="${f.fieldName}" column="${f.columnName}"/>
        <#else>
        <result property="${f.fieldName}" column="${f.columnName}"/>
        </#if>
</#list>
    </resultMap>

    <sql id="columns">
<#list fields as f>${f.columnName}<#sep>, </#sep></#list>
    </sql>

    <insert id="insert" parameterType="${basePackage}.entity.${entityName}"
            useGeneratedKeys="true" keyProperty="${idField}">
        INSERT INTO ${table.name}
        (<#list fields as f><#if !f.primaryKey || !f.autoIncrement>${f.columnName}<#sep>, </#sep></#if></#list>)
        VALUES
        (<#list fields as f><#if !f.primaryKey || !f.autoIncrement>${'#'}{${f.fieldName}}<#sep>, </#sep></#if></#list>)
    </insert>

    <update id="update" parameterType="${basePackage}.entity.${entityName}">
        UPDATE ${table.name}
        SET
<#list fields as f><#if !f.primaryKey>            ${f.columnName} = ${'#'}{${f.fieldName}}<#sep>,
</#sep></#if></#list>

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
</mapper>
