package ${basePackage}.mapper;

import ${basePackage}.entity.${entityName};
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ${entityName}Mapper {

    int insert(${entityName} record);

    int update(${entityName} record);

    int deleteById(@Param("id") ${idType} id);

    ${entityName} findById(@Param("id") ${idType} id);

    List<${entityName}> findAll(@Param("offset") int offset, @Param("limit") int limit);

    long count();
}
