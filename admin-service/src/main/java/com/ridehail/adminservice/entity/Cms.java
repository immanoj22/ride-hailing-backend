package com.ridehail.adminservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA equivalent of Admin-service/modals/cms.schema.js.
 */
@Entity
@Table(name = "cms_pages")
@Getter
@Setter
@NoArgsConstructor
public class Cms extends BaseEntity {

    @Column(name = "name", nullable = false)
    private String name = "";

    @Column(name = "slug", nullable = false)
    private String slug = "";

    @Column(name = "page", nullable = false)
    private String page = "";

    /** Mongoose declares this as plain Number (no enum enforced at the schema level). */
    @Column(name = "type")
    private Integer type;

    @Column(name = "link", nullable = false)
    private String link = "";

    @Column(name = "image", nullable = false)
    private String image = "";

    @Column(name = "question", nullable = false, columnDefinition = "text")
    private String question = "";

    @Column(name = "answer", nullable = false, columnDefinition = "text")
    private String answer = "";

    @Column(name = "is_deleted", nullable = false)
    private boolean deleted = false;
}
