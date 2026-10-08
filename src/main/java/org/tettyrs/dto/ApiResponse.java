package org.tettyrs.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    public T data;
    public ResponseMeta meta;
    public ApiError error;

    public ApiResponse() {
    }

    public ApiResponse(T data) {
        this.data = data;
        this.meta = new ResponseMeta();
    }

    public ApiResponse(T data, ResponseMeta meta) {
        this.data = data;
        this.meta = meta;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data);
    }

    public static <T> ApiResponse<T> success(T data, ResponseMeta meta){
        return new ApiResponse<>(data, meta);
    }

    public static <T> ApiResponse<T> error(String code, String message){
        ApiResponse<T> response = new ApiResponse<>();
        response.error = new ApiError(code, message);
        return response;
    }

    public static <T> ApiResponse<T> error(String code, String message, String field){
        ApiResponse<T> response = new ApiResponse<>();
        response.error = new ApiError(code, message, field);
        return response;
    }

    public static <T> ApiResponse error(ApiError error){
        ApiResponse<T> response = new ApiResponse<>();
        response.error = error;
        return response;
    }


    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ResponseMeta {
        public LocalDateTime timestamp;
        public String version = "1.0";
        public String path;
        public Integer page;
        public Integer limit;
        public Long total;


        public ResponseMeta() {
            this.timestamp = LocalDateTime.now();
        }

        public ResponseMeta(String path) {
            this.path = path;
        }

        public ResponseMeta(String path, Integer page, Integer limit, Long total) {
            this.path = path;
            this.page = page;
            this.limit = limit;
            this.total = total;
        }
    }


}
