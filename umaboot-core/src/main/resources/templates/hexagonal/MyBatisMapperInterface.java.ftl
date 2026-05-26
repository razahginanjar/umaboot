package ${basePackage}.adapter.out.persistence;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * MyBatis Mapper for {@link ${entityName}PersistenceModel} (XML-backed).
 *
 * <p>SQL lives in {@code resources/mapper/${entityName}MyBatisMapper.xml}.</p>
 */
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
