package io.yak.ops.business.dataservice.access;

public class DataServiceUnauthorizedException extends RuntimeException {
  public DataServiceUnauthorizedException(String message) { super(message); }
}
