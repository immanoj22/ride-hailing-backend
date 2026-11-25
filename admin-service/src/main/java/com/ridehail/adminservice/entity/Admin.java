package com.ridehail.adminservice.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA equivalent of Admin-service/modals/admin.schema.js.
 */
@Entity
@Table(name = "admins")
@Getter
@Setter
@NoArgsConstructor
public class Admin extends BaseEntity {

    @Column(name = "fullname", nullable = false)
    private String fullname = "";

    @Column(name = "email", nullable = false)
    private String email = "";

    @Column(name = "password", nullable = false)
    private String password = "";

    @Column(name = "mobileno", nullable = false)
    private String mobileno = "";

    @Column(name = "profile", nullable = false)
    private String profile = "";

    @Column(name = "isactive", nullable = false)
    private boolean active = true;
}
