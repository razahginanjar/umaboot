package ${basePackage}.adapter.out.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link ${entityName}JpaEntity}.
 * Internal to the persistence adapter — domain code goes through the
 * {@code ${entityName}Repository} port instead.
 */
interface ${entityName}JpaRepository extends JpaRepository<${entityName}JpaEntity, ${idType}> {
}
