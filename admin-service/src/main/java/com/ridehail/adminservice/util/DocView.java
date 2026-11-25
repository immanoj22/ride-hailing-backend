package com.ridehail.adminservice.util;

import java.util.LinkedHashMap;
import java.util.Map;

import com.ridehail.adminservice.entity.Admin;
import com.ridehail.adminservice.entity.Cms;
import com.ridehail.adminservice.entity.Faq;

/**
 * Builds response maps whose keys match the original Mongoose document JSON exactly (camelCase
 * field names, {@code _id}, {@code delete}, {@code createdAt}/{@code updatedAt}, {@code __v}),
 * so the wire shape returned to clients is unchanged by the migration.
 */
public final class DocView {

    private DocView() {
    }

    /** Full admin document (login / register / getAdminId paths - no Mongoose projection). */
    public static Map<String, Object> admin(Admin a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", a.getId());
        m.put("fullname", a.getFullname());
        m.put("email", a.getEmail());
        m.put("password", a.getPassword());
        m.put("mobileno", a.getMobileno());
        m.put("profile", a.getProfile());
        m.put("isactive", a.isActive());
        m.put("createdAt", a.getCreatedAt());
        m.put("updatedAt", a.getUpdatedAt());
        m.put("__v", 0);
        return m;
    }

    /** Projected admin document: {@code getAdmin()} uses {@code .select("_id fullname email password profile mobileno")}. */
    public static Map<String, Object> adminProjected(Admin a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", a.getId());
        m.put("fullname", a.getFullname());
        m.put("email", a.getEmail());
        m.put("password", a.getPassword());
        m.put("profile", a.getProfile());
        m.put("mobileno", a.getMobileno());
        return m;
    }

    public static Map<String, Object> cms(Cms c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", c.getId());
        m.put("name", c.getName());
        m.put("slug", c.getSlug());
        m.put("page", c.getPage());
        m.put("type", c.getType());
        m.put("link", c.getLink());
        m.put("image", c.getImage());
        m.put("question", c.getQuestion());
        m.put("answer", c.getAnswer());
        m.put("delete", c.isDeleted());
        m.put("createdAt", c.getCreatedAt());
        m.put("updatedAt", c.getUpdatedAt());
        m.put("__v", 0);
        return m;
    }

    public static Map<String, Object> faq(Faq f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("_id", f.getId());
        m.put("question", f.getQuestion());
        m.put("answer", f.getAnswer());
        m.put("type", f.getType());
        m.put("delete", f.isDeleted());
        m.put("createdAt", f.getCreatedAt());
        m.put("updatedAt", f.getUpdatedAt());
        m.put("__v", 0);
        return m;
    }
}
