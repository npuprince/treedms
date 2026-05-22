package com.example.treedms.exception;

import java.time.LocalDateTime;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(AppException.class)
    public ResponseEntity<ApiError> handleAppException(AppException ex) {
        return build(ex.getStatus(), ex.getMessage());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex) {
        String message = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::formatFieldError)
                .collect(Collectors.joining("; "));
        return build(HttpStatus.BAD_REQUEST, message);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleMaxUploadSize() {
        return build(HttpStatus.BAD_REQUEST, "文件大小超过系统限制");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex) {
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "服务器内部错误: " + ex.getMessage());
    }

    private String formatFieldError(FieldError error) {
        return error.getField() + " " + error.getDefaultMessage();
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message) {
        ApiError error = new ApiError(status.value(), message, LocalDateTime.now());
        return ResponseEntity.status(status).body(error);
    }
}
