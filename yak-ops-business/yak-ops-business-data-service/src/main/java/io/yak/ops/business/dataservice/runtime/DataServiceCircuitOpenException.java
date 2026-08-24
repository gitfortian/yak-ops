package io.yak.ops.business.dataservice.runtime;

public class DataServiceCircuitOpenException extends RuntimeException {
  public DataServiceCircuitOpenException(String message) {
    super(message);
  }
}
