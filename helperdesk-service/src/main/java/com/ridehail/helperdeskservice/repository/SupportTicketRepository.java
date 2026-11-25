package com.ridehail.helperdeskservice.repository;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.ridehail.helperdeskservice.entity.SupportTicket;

/**
 * Mirrors the lookups performed in helperdesk.controller.js/helperdesk.services.js
 * (getSupportTickets/findOneTicket/updateTicket/supportTicketSave).
 *
 * The admin listing (getSupportTicketsByAdmin) filters by complainType/status and free-text
 * searches subject/from/messages.message, so this also extends JpaSpecificationExecutor for the
 * implementation step to build that dynamic query with a Specification instead of many derived
 * method combinations.
 */
public interface SupportTicketRepository extends JpaRepository<SupportTicket, UUID>,
        JpaSpecificationExecutor<SupportTicket> {

    /** getSupportTicketsByUser: query = { userId, from: "user" } or { userId, from: "driver" }. */
    Page<SupportTicket> findByUserIdAndFromType(UUID userId, String fromType, Pageable pageable);
}
