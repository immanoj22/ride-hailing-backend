package com.ridehail.adminservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.adminservice.entity.Admin;

public interface AdminRepository extends JpaRepository<Admin, UUID> {

    Optional<Admin> findByEmail(String email);

    /** update_admin's "email already taken by someone else" check: {@code {email, _id: {$ne: id}}}. */
    Optional<Admin> findByEmailAndIdNot(String email, UUID id);

    /** get_Admin (no filter in the original Mongoose query - just grabs whichever record exists first). */
    Optional<Admin> findFirstByOrderByCreatedAtAsc();
}
