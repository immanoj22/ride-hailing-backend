package com.ridehail.adminservice.entity;

import java.util.UUID;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA equivalent of Admin-service/modals/adminactivity.schema.js - an audit trail of admin
 * logins/logouts/CRUD actions. adminId is a real FK because Admin lives in this same service's
 * database.
 */
@Entity
@Table(name = "admin_activities")
@Getter
@Setter
@NoArgsConstructor
public class AdminActivity extends BaseEntity {

    @Column(name = "admin_id", nullable = false)
    private UUID adminId;

    @Column(name = "action", nullable = false)
    private String action;

    @Column(name = "ip", nullable = false)
    private String ip = "";

    @Column(name = "device", nullable = false)
    private String device = "";

    /** Mongoose {@code meta: {type: Object, default: {}}} - genuinely free-form metadata. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "meta", nullable = false, columnDefinition = "jsonb")
    private JsonNode meta;
}
