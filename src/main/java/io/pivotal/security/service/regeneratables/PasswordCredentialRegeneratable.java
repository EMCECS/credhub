package io.pivotal.security.service.regeneratables;

import io.pivotal.security.domain.PasswordCredential;
import io.pivotal.security.exceptions.ParameterizedValidationException;
import io.pivotal.security.request.BaseCredentialGenerateRequest;
import io.pivotal.security.request.PasswordGenerateRequest;
import io.pivotal.security.request.StringGenerationParameters;

public class PasswordCredentialRegeneratable implements Regeneratable<PasswordCredential> {

  @Override
  public BaseCredentialGenerateRequest createGenerateRequest(PasswordCredential passwordCredential) {
    PasswordGenerateRequest generateRequest = new PasswordGenerateRequest();

    generateRequest.setName(passwordCredential.getName());
    generateRequest.setType(passwordCredential.getCredentialType());
    StringGenerationParameters generationParameters;
    generationParameters = passwordCredential.getGenerationParameters();
    generateRequest.setOverwrite(true);

    if (generationParameters == null) {
      throw new ParameterizedValidationException(
          "error.cannot_regenerate_non_generated_password");
    }
    generateRequest.setGenerationParameters(generationParameters);
    return generateRequest;
  }
}
