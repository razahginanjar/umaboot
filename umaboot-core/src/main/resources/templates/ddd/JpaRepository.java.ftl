package ${basePackage}.infrastructure.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

interface ${entityName}JpaRepository extends JpaRepository<${entityName}JpaEntity, ${idType}> {
}
