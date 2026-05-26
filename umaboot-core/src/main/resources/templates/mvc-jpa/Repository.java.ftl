package ${basePackage}.repository;

import ${basePackage}.entity.${entityName};
<#if paginationCursor>
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
</#if>
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ${entityName}Repository extends JpaRepository<${entityName}, ${idType}> {
<#if paginationCursor>

    /**
     * Cursor pagination: returns up to {@code pageable.pageSize} entities whose
     * id is strictly greater than {@code cursorId}, ordered by id ascending.
     * Use {@code Slice} (not {@code Page}) so we don't pay for a count query.
     */
    Slice<${entityName}> findByIdGreaterThanOrderByIdAsc(${idType} cursorId, Pageable pageable);
</#if>
}
