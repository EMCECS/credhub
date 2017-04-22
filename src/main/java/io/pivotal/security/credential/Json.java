package io.pivotal.security.credential;

import com.fasterxml.jackson.annotation.JsonValue;

import java.util.Map;

public class Json implements CredentialValue {
  private final Map<String, Object> value;

  public Json(Map<String, Object> json) {
    this.value = json;
  }

  @JsonValue
  public Map<String, Object> getValue() {
    return value;
  }
}
