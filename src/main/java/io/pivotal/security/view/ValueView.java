package io.pivotal.security.view;

import io.pivotal.security.credential.StringCredentialValue;
import io.pivotal.security.domain.ValueCredentialVersion;

@SuppressWarnings("unused")
public class ValueView extends CredentialView {

  public ValueView() {}

  ValueView(ValueCredentialVersion valueCredential) {
    super(
        valueCredential.getVersionCreatedAt(),
        valueCredential.getUuid(),
        valueCredential.getName(),
        valueCredential.getCredentialType(),
        new StringCredentialValue((String) valueCredential.getValue()),
        false);
  }
}
