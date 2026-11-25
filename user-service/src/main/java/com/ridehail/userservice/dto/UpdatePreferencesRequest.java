package com.ridehail.userservice.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/** POST /update-preferences. Mirrors uservalidation.js's preferenceSchema. */
public class UpdatePreferencesRequest {

    @NotNull
    @Valid
    private Preferences preferences;

    public Preferences getPreferences() {
        return preferences;
    }

    public void setPreferences(Preferences preferences) {
        this.preferences = preferences;
    }

    public static class Preferences {
        private Boolean allowSmsInvoice;
        private Boolean allowWhatsAppUpdates;
        private Boolean allowEmailInvoice;
        private Boolean allowMobileNotification;

        public Boolean getAllowSmsInvoice() {
            return allowSmsInvoice;
        }

        public void setAllowSmsInvoice(Boolean allowSmsInvoice) {
            this.allowSmsInvoice = allowSmsInvoice;
        }

        public Boolean getAllowWhatsAppUpdates() {
            return allowWhatsAppUpdates;
        }

        public void setAllowWhatsAppUpdates(Boolean allowWhatsAppUpdates) {
            this.allowWhatsAppUpdates = allowWhatsAppUpdates;
        }

        public Boolean getAllowEmailInvoice() {
            return allowEmailInvoice;
        }

        public void setAllowEmailInvoice(Boolean allowEmailInvoice) {
            this.allowEmailInvoice = allowEmailInvoice;
        }

        public Boolean getAllowMobileNotification() {
            return allowMobileNotification;
        }

        public void setAllowMobileNotification(Boolean allowMobileNotification) {
            this.allowMobileNotification = allowMobileNotification;
        }
    }
}
