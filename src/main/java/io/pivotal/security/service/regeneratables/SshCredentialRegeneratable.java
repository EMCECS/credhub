package io.pivotal.security.service.regeneratables;

import io.pivotal.security.domain.SshCredential;
import io.pivotal.security.request.BaseCredentialGenerateRequest;
import io.pivotal.security.request.SshGenerateRequest;
import io.pivotal.security.request.SshGenerationParameters;

public class SshCredentialRegeneratable implements Regeneratable<SshCredential> {

  @Override
  public BaseCredentialGenerateRequest createGenerateRequest(SshCredential sshCredential) {
    SshGenerateRequest generateRequest = new SshGenerateRequest();

    generateRequest.setName(sshCredential.getName());
    generateRequest.setType(sshCredential.getCredentialType());
    SshGenerationParameters generationParameters = new SshGenerationParameters();
    generationParameters.setSshComment(sshCredential.getComment());
    generateRequest.setGenerationParameters(generationParameters);
    generateRequest.setOverwrite(true);
    return generateRequest;
  }
}
