package com.artverse.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.exception.NotRoleException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Map<String, Object>> handleBusiness(BusinessException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("detail", ex.getMessage());
        if (ex.getProvider() != null) {
            body.put("provider", ex.getProvider());
        }
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    @ExceptionHandler(NotLoginException.class)
    public ResponseEntity<Map<String, Object>> handleNotLogin(NotLoginException ex) {
        log.info("Authentication required: {}", ex.getMessage());
        return ResponseEntity.status(401).body(Map.of(
                "detail", "Login expired",
                "code", "AUTH_EXPIRED"));
    }

    @ExceptionHandler(NotRoleException.class)
    public ResponseEntity<Map<String, Object>> handleNotRole(NotRoleException ex) {
        log.warn("Role check failed: {}", ex.getMessage());
        return ResponseEntity.status(403).body(Map.of(
                "detail", "权限不足"));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        List<Map<String, String>> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", e.getDefaultMessage()))
                .collect(Collectors.toList());
        String detail = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("detail", detail);
        body.put("errors", fieldErrors);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGeneral(Exception ex) {
        if (isClientDisconnect(ex)) {
            log.debug("Client disconnected while response was being written: {}", ex.getMessage());
            return ResponseEntity.noContent().build();
        }
        log.error("Unhandled exception", ex);
        return ResponseEntity.internalServerError().body(Map.of("detail", "Internal server error"));
    }

    private boolean isClientDisconnect(Throwable ex) {
        Throwable current = ex;
        while (current != null) {
            String className = current.getClass().getName();
            String message = current.getMessage();
            if (current instanceof IOException || className.contains("ClientAbortException")) {
                if (message == null || isClientDisconnectMessage(message)) {
                    return true;
                }
            }
            if (message != null && isClientDisconnectMessage(message)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private boolean isClientDisconnectMessage(String message) {
        return message.contains("你的主机中的软件中止了一个已建立的连接")
                || message.contains("Broken pipe")
                || message.contains("Connection reset by peer")
                || message.contains("An established connection was aborted")
                || message.contains("远程主机强迫关闭了一个现有的连接");
    }
}
