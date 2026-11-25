package com.ridehail.adminservice.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.adminservice.entity.Cms;

public interface CmsRepository extends JpaRepository<Cms, UUID> {

    /** add_cms: {@code getCMS({name, delete:false})} uniqueness check. */
    Optional<Cms> findByNameAndDeletedFalse(String name);

    /** edit_cms: {@code getCMS({name, delete:false, _id:{$nin:[id]}})} uniqueness check on edit. */
    Optional<Cms> findByNameAndDeletedFalseAndIdNot(String name, UUID id);

    /** getAllcms: {@code getAllCMS({delete:false})}. */
    Page<Cms> findByDeletedFalse(Pageable pageable);

    /** getCmsByPage: {@code getAllCMS({page})} - no delete filter in the original query. */
    List<Cms> findByPage(String page);
}
