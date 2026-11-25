package com.ridehail.helperdeskservice.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.ridehail.helperdeskservice.entity.SupportMessage;

/** Child rows of a support ticket's embedded "messages" array (support.schema.js). */
public interface SupportMessageRepository extends JpaRepository<SupportMessage, UUID> {

    List<SupportMessage> findBySupportTicketIdOrderByCreatedAtAsc(UUID supportTicketId);
}
