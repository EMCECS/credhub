package io.pivotal.security.request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.pivotal.security.domain.Encryptor;
import io.pivotal.security.domain.NamedPasswordSecret;
import org.hibernate.validator.constraints.NotEmpty;

public class PasswordSetRequest extends BaseSecretSetRequest<NamedPasswordSecret> {

  @NotEmpty(message = "error.missing_value")
  @JsonProperty("value")
  private String password;
  @JsonIgnore
  private StringGenerationParameters generationParameters;

  public String getPassword() {
    return password;
  }

  public void setPassword(String password) {
    this.password = password;
  }

  public void setGenerationParameters(StringGenerationParameters generationParameters) {
    this.generationParameters = generationParameters;
  }

  @Override
  @JsonIgnore
  public NamedPasswordSecret createNewVersion(NamedPasswordSecret existing, Encryptor encryptor) {
    return NamedPasswordSecret.createNewVersion(
        existing,
        getName(),
        getPassword(),
        encryptor,
        getAccessControlEntries()
    );
  }
}
