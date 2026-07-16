package com.forumx.tenant.repository;

import java.util.Optional;

import com.forumx.tenant.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA Repository for {@link Tenant} entity.
 */
@Repository
public interface TenantRepository extends JpaRepository<Tenant, Long> {

    /**
     * Finds a tenant by its unique slug.
     *
     * @param slug the slug of the tenant
     * @return an Optional containing the found tenant, or empty if not found
     */
    Optional<Tenant> findBySlug(String slug);

    /**
     * Finds a tenant by its ID and ensures it is not soft-deleted.
     *
     * @param id the tenant ID
     * @return an Optional containing the found tenant, or empty if not found or deleted
     */
    Optional<Tenant> findByIdAndDeletedFalse(Long id);
}
