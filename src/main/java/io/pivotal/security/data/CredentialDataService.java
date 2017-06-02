package io.pivotal.security.data;

import io.pivotal.security.entity.Credential;
import io.pivotal.security.exceptions.EntryNotFoundException;
import io.pivotal.security.repository.CredentialRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CredentialDataService {
  private final CredentialRepository credentialRepository;

  @Autowired
  public CredentialDataService(CredentialRepository credentialRepository) {
    this.credentialRepository = credentialRepository;
  }

  public Credential find(String name) {
    return credentialRepository.findOneByNameIgnoreCase(name);
  }

  public Credential findOrThrow(String name) {
    final Credential credential = find(name);

    if (credential == null) {
      throw new EntryNotFoundException("error.resource_not_found");
    }

    return credential;
  }

  public Credential save(Credential credential) {
    return credentialRepository.saveAndFlush(credential);
  }

  public boolean delete(String credentialName) {
    return credentialRepository.deleteByNameIgnoreCase(credentialName) > 0;
  }

  public List<Credential> findAll() {
    return credentialRepository.findAll();
  }
}
