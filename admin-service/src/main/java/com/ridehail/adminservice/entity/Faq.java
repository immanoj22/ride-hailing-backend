package com.ridehail.adminservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA equivalent of Admin-service/modals/faq.schema.js.
 */
@Entity
@Table(name = "faqs")
@Getter
@Setter
@NoArgsConstructor
public class Faq extends BaseEntity {

    @Column(name = "question", nullable = false, columnDefinition = "text")
    private String question = "";

    @Column(name = "answer", nullable = false, columnDefinition = "text")
    private String answer = "";

    /** Mongoose enum: ["user", "driver"], default "user". */
    @Column(name = "type", nullable = false)
    private String type = "user";

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;
}
