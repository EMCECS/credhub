package io.pivotal.security.domain;

import io.pivotal.security.credential.RsaKey;
import io.pivotal.security.entity.RsaCredentialData;
import io.pivotal.security.request.AccessControlEntry;
import io.pivotal.security.service.Encryption;

import java.util.ArrayList;
import java.util.List;

public class RsaCredential extends Credential<RsaCredential> {

  private RsaCredentialData delegate;
  private String privateKey;

  public RsaCredential(RsaCredentialData delegate) {
    super(delegate);
    this.delegate = delegate;
  }

  public RsaCredential(String name) {
    this(new RsaCredentialData(name));
  }

  public RsaCredential() {
    this(new RsaCredentialData());
  }

  public static RsaCredential createNewVersion(RsaCredential existing, String name,
                                               RsaKey rsaKey, Encryptor encryptor,
                                               List<AccessControlEntry> accessControlEntries) {
    RsaCredential credential;

    if (existing == null) {
      credential = new RsaCredential(name);
    } else {
      credential = new RsaCredential();
      credential.copyNameReferenceFrom(existing);
    }

    if (accessControlEntries == null) {
      accessControlEntries = new ArrayList<>();
    }

    credential.setAccessControlList(accessControlEntries);

    credential.setEncryptor(encryptor);
    credential.setPrivateKey(rsaKey.getPrivateKey());
    credential.setPublicKey(rsaKey.getPublicKey());

    return credential;
  }

  public int getKeyLength() {
    return delegate.getKeyLength();
  }

  public String getPublicKey() {
    return delegate.getPublicKey();
  }

  public RsaCredential setPublicKey(String publicKey) {
    this.delegate.setPublicKey(publicKey);
    return this;
  }

  public String getPrivateKey() {
    return privateKey;
  }

  public RsaCredential setPrivateKey(String privateKey) {
    this.privateKey = privateKey;
    return this;
  }

  public void rotate() {
    String decryptedValue = this.getPrivateKey();
    this.setPrivateKey(decryptedValue);
  }


  @Override
  public String getCredentialType() {
    return delegate.getCredentialType();
  }
}
