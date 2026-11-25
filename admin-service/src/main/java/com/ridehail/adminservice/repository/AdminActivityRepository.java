package com.ridehail.adminservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.adminservice.entity.AdminActivity;

public interface AdminActivityRepository extends JpaRepository<AdminActivity, UUID> {

    List<AdminActivity> findByAdminIdOrderByCreatedAtDesc(UUID adminId);
}
