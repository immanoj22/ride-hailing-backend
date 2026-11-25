package com.ridehail.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Mirrors the legacy Node response envelope built by {@code sendResponse(res, status, success,
 * statusCode, message, data)} in common-service/config/common_functions.js: {@code { status,
 * message, data, success } }. Field order/names are preserved for wire compatibility.
 */
@JsonInclude(JsonInclude.Include.ALWAYS)
public class ApiResponse<T> {

    private boolean status;
    private String message;
    private T data;
    private String success;

    public ApiResponse() {
    }

    public ApiResponse(boolean status, String message, T data, String success) {
        this.status = status;
        this.message = message;
        this.data = data;
        this.success = success;
    }

    public boolean isStatus() {
        return status;
    }

    public void setStatus(boolean status) {
        this.status = status;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public String getSuccess() {
        return success;
    }

    public void setSuccess(String success) {
        this.success = success;
    }
}
