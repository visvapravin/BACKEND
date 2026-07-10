package com.forumx.auth.repository;

import java.util.List;
import java.util.Optional;

import com.forumx.auth.entity.Role;
import com.forumx.auth.enums.RoleType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface RoleRepository extends JpaRepository<Role, Long>, JpaSpecificationExecutor<Role> {

    Optional<Role> findByRoleName(RoleType roleType);

    boolean existsByRoleName(RoleType roleType);

    List<Role> findAllByActiveTrue();

    @Query("""
            SELECT r FROM Role r
            WHERE r.active = true
            ORDER BY r.roleName ASC
            """)
    List<Role> findAllActiveOrderByName();

    @Query("""
            SELECT DISTINCT r FROM Role r
            JOIN r.userRoles ur
            WHERE ur.user.id = :userId
              AND r.active = true
              AND ur.active = true
            """)
    List<Role> findRolesByUserId(@Param("userId") Long userId);
}
