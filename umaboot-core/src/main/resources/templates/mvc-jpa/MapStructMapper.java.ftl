package ${basePackage}.mapper;

import ${basePackage}.dto.${entityName}RequestDTO;
import ${basePackage}.dto.${entityName}ResponseDTO;
import ${basePackage}.entity.${entityName};
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

/**
 * MapStruct-generated mapper. Activated via {@code generation.jpa.useMapStruct=true}.
 *
 * <p>The {@code componentModel = "spring"} setting injects this mapper as a Spring bean.</p>
 */
@Mapper(componentModel = "spring")
public interface ${entityName}DtoMapper {

    ${entityName} toEntity(${entityName}RequestDTO dto);

    ${entityName}ResponseDTO toResponse(${entityName} entity);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    void updateEntity(@MappingTarget ${entityName} entity, ${entityName}RequestDTO dto);
}
