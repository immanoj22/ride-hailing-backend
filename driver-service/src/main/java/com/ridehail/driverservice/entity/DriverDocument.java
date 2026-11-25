package com.ridehail.driverservice.entity;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.type.SqlTypes;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Equivalent of one entry of driver.schema.js's embedded "documents" array (DocumentSchema). Every
 * new driver is seeded with the 6 default document rows the Mongoose schema hard-codes (aadhaar,
 * drivinglicense, RC, insurance, vehicle_images, permit) - service-layer responsibility on driver
 * creation, not enforced by this migration.
 */
@Entity
@Table(name = "driver_documents")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DriverDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "driver_id", nullable = false)
    private Driver driver;

    /** e.g. "aadhaar", "drivinglicense", "RC", "insurance", "vehicle_images", "permit". */
    @Column(name = "doc_key", nullable = false, length = 50)
    private String docKey;

    @Column(name = "title", nullable = false)
    private String title;

    /** "pending" | "verified" | "rejected". */
    @Builder.Default
    @Column(name = "status", nullable = false, length = 20)
    private String status = "pending";

    /** Free-form sub-key/index -> uploaded file url map. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "files", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private JsonNode files = JsonNodeFactory.instance.objectNode();

    /** Free-form sub-key/index -> per-file review status map. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "file_status", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private JsonNode fileStatus = JsonNodeFactory.instance.objectNode();

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
