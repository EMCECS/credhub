package io.pivotal.security.handler;

import io.pivotal.security.audit.AuditingOperationCode;
import io.pivotal.security.audit.EventAuditRecordParameters;
import io.pivotal.security.auth.UserContext;
import io.pivotal.security.data.CredentialDataService;
import io.pivotal.security.domain.Credential;
import io.pivotal.security.exceptions.EntryNotFoundException;
import io.pivotal.security.exceptions.InvalidQueryParameterException;
import io.pivotal.security.service.PermissionService;
import io.pivotal.security.view.FindByCaResults;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;

import static io.pivotal.security.request.PermissionOperation.DELETE;
import static io.pivotal.security.request.PermissionOperation.READ;

@Component
public class CredentialsHandler {
  private final CredentialDataService credentialDataService;
  private final PermissionService permissionService;

  @Autowired
  public CredentialsHandler(CredentialDataService credentialDataService,
      PermissionService permissionService) {
    this.credentialDataService = credentialDataService;
    this.permissionService = permissionService;
  }

  public void deleteCredential(String credentialName, UserContext userContext) {
    if (!permissionService.hasPermission(userContext.getAclUser(), credentialName, DELETE)) {
      throw new EntryNotFoundException("error.credential.invalid_access");
    }

    boolean deleteSucceeded = credentialDataService.delete(credentialName);

    if (!deleteSucceeded) {
      throw new EntryNotFoundException("error.credential.invalid_access");
    }
  }

  public List<Credential> getNCredentialVersions(
      String credentialName,
      Integer numberOfVersions, UserContext userContext,
      List<EventAuditRecordParameters> auditRecordParametersList
  ) {
    EventAuditRecordParameters auditRecordParameters = new EventAuditRecordParameters(
        AuditingOperationCode.CREDENTIAL_ACCESS, credentialName);
    auditRecordParametersList.add(auditRecordParameters);

    List<Credential> credentials;
    if (numberOfVersions == null) {
      credentials = credentialDataService.findAllByName(credentialName);
    } else {
      if (numberOfVersions < 0) {
        throw new InvalidQueryParameterException("error.invalid_query_parameter", "versions");
      }
      credentials = credentialDataService.findNByName(credentialName, numberOfVersions);
    }

    // We need this extra check in case permissions aren't being enforced.
    if (credentials.isEmpty()
        || !permissionService.hasPermission(userContext.getAclUser(), credentialName, READ)) {
      throw new EntryNotFoundException("error.credential.invalid_access");
    }

    return credentials;
  }

  public List<Credential> getAllCredentialVersions(
      String credentialName,
      UserContext userContext,
      List<EventAuditRecordParameters> auditRecordParametersList
  ) {
    return getNCredentialVersions(credentialName, null, userContext, auditRecordParametersList);
  }

  public Credential getMostRecentCredentialVersion(
      String credentialName,
      UserContext userContext,
      List<EventAuditRecordParameters> auditRecordParametersList
  ) {
    Credential credential =
        getNCredentialVersions(credentialName, 1, userContext, auditRecordParametersList)
            .get(0);

    return credential;
  }

  public Credential getCredentialVersionByUUID(
      String credentialUUID,
      UserContext userContext,
      List<EventAuditRecordParameters> auditRecordParametersList
  ) {
    EventAuditRecordParameters eventAuditRecordParameters = new EventAuditRecordParameters(
        AuditingOperationCode.CREDENTIAL_ACCESS
    );

    Credential credential = credentialDataService.findByUuid(credentialUUID);

    if (credential != null) {
      eventAuditRecordParameters.setCredentialName(credential.getName());
    }

    auditRecordParametersList.add(eventAuditRecordParameters);

    if (credential == null ||
        !permissionService.hasPermission(userContext.getAclUser(), credential.getName(), READ)) {
      throw new EntryNotFoundException("error.credential.invalid_access");
    }

    return credential;
  }

  public FindByCaResults getCredentialsByCaName(
      String signerName
  ) {
    FindByCaResults results = new FindByCaResults();
    List<String> certificateNames = credentialDataService.findAllCertificateCredentialsByCaName(signerName);

    final HashSet<String> credentialNamesSet = new HashSet<>(certificateNames);

    results.setCredentials(credentialNamesSet);
    return results;
  }
}
