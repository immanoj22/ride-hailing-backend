package com.ridehail.adminservice.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.adminservice.entity.Faq;

public interface FaqRepository extends JpaRepository<Faq, UUID> {

    /** add_faq: {@code getFAQ({question, delete:false})} uniqueness check. */
    Optional<Faq> findByQuestionAndDeletedFalse(String question);

    /** edit_faq: {@code getFAQ({question, delete:false, _id:{$nin:[id]}})} uniqueness check on edit. */
    Optional<Faq> findByQuestionAndDeletedFalseAndIdNot(String question, UUID id);

    /** getAllFaq: {@code getAllFAQ({delete:false})}. */
    Page<Faq> findByDeletedFalse(Pageable pageable);

    /** getFaqByType: {@code getAllFAQ({type, delete:false})}. */
    Page<Faq> findByTypeAndDeletedFalse(String type, Pageable pageable);
}
