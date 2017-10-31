package io.pivotal.security.request;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import org.apache.commons.lang3.StringUtils;

import javax.validation.constraints.NotNull;

@JsonAutoDetect
@SuppressWarnings("unused")
public class RegenerateRequest {

  @NotNull(message = "error.missing_name")
  private String name;
  private boolean transitional;

  public RegenerateRequest() {
        /* this needs to be there for jackson to be happy */
  }

  public RegenerateRequest(String name) {
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = StringUtils.prependIfMissing(name, "/");
  }

  public boolean isTransitional() {
    return transitional;
  }

  public void setTransitional(boolean transitional) {
    this.transitional = transitional;
  }
}
