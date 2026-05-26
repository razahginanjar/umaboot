package ${basePackage}.infrastructure.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface ${entityName}MyBatisMapper {

    int insert(${entityName}PersistenceModel record);

    int update(${entityName}PersistenceModel record);

    int deleteById(@Param("id") ${idType} id);

    ${entityName}PersistenceModel findById(@Param("id") ${idType} id);

    List<${entityName}PersistenceModel> findAll(@Param("offset") int offset, @Param("limit") int limit);

    long count();

    int existsById(@Param("id") ${idType} id);
}
