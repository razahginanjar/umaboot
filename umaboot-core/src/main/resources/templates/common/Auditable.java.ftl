package ${auditablePackage};

import ${eeNamespace}.persistence.Column;
import ${eeNamespace}.persistence.EntityListeners;
import ${eeNamespace}.persistence.MappedSuperclass;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;

/**
 * {@code @MappedSuperclass} that supplies audit columns to any entity that
 * extends it. Generated when Umaboot detects audit columns on at least one
 * entity in your schema.
 *
 * <p>Activated by {@code @EnableJpaAuditing} on the application class.</p>
 */
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class Auditable {

<#if anyHasCreatedAt>
    @CreatedDate
    @Column(name = "${auditCreatedAt}", nullable = false, updatable = false)
    private Instant createdAt;

</#if>
<#if anyHasUpdatedAt>
    @LastModifiedDate
    @Column(name = "${auditUpdatedAt}")
    private Instant updatedAt;

</#if>
<#if anyHasCreatedBy>
    @CreatedBy
    @Column(name = "${auditCreatedBy}", nullable = false, updatable = false, length = 64)
    private String createdBy;

</#if>
<#if anyHasUpdatedBy>
    @LastModifiedBy
    @Column(name = "${auditUpdatedBy}", length = 64)
    private String updatedBy;

</#if>
<#if anyHasCreatedAt>
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

</#if>
<#if anyHasUpdatedAt>
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

</#if>
<#if anyHasCreatedBy>
    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

</#if>
<#if anyHasUpdatedBy>
    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }
</#if>
}
