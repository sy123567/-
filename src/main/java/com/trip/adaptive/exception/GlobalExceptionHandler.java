package com.trip.adaptive.exception;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
  record ApiError(
      LocalDateTime timestamp,
      int status,
      String error,
      String message,
      String path,
      Map<String, String> fieldErrors) {}

  @ExceptionHandler(ResourceNotFoundException.class)
  ResponseEntity<ApiError> notFound(ResourceNotFoundException e, HttpServletRequest r) {
    return body(404, "Not Found", e.getMessage(), r, null);
  }

  @ExceptionHandler(BusinessException.class)
  ResponseEntity<ApiError> business(BusinessException e, HttpServletRequest r) {
    return body(409, "Business Conflict", e.getMessage(), r, null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> invalid(MethodArgumentNotValidException e, HttpServletRequest r) {
    Map<String, String> m = new HashMap<>();
    e.getBindingResult().getFieldErrors().forEach(x -> m.put(x.getField(), x.getDefaultMessage()));
    return body(400, "Bad Request", "请求参数校验失败", r, m);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> generic(Exception e, HttpServletRequest r) {
    return body(500, "Internal Server Error", e.getMessage(), r, null);
  }

  private ResponseEntity<ApiError> body(
      int s, String e, String m, HttpServletRequest r, Map<String, String> f) {
    return ResponseEntity.status(s)
        .body(new ApiError(LocalDateTime.now(), s, e, m, r.getRequestURI(), f));
  }
}
