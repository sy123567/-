package com.trip.adaptive.exception;

/** 已登录但没有权限访问该资源，映射为 HTTP 403。 */
public class ForbiddenException extends RuntimeException {
  public ForbiddenException(String message) {
    super(message);
  }
}
