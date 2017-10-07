package io.pivotal.security.service;

import io.pivotal.security.audit.AuditingOperationCode;
import io.pivotal.security.audit.EventAuditRecordParameters;
import io.pivotal.security.auth.UserContext;
import io.pivotal.security.constants.CredentialType;
import io.pivotal.security.credential.CredentialValue;
import io.pivotal.security.data.CredentialVersionDataService;
import io.pivotal.security.domain.CredentialFactory;
import io.pivotal.security.domain.CredentialVersion;
import io.pivotal.security.exceptions.EntryNotFoundException;
import io.pivotal.security.exceptions.InvalidAclOperationException;
import io.pivotal.security.exceptions.ParameterizedValidationException;
import io.pivotal.security.exceptions.PermissionException;
import io.pivotal.security.request.GenerationParameters;
import io.pivotal.security.request.PermissionEntry;
import io.pivotal.security.request.PermissionOperation;
import io.pivotal.security.view.CredentialView;
import io.pivotal.security.view.FindCredentialResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static io.pivotal.security.audit.AuditingOperationCode.ACL_UPDATE;
import static io.pivotal.security.audit.AuditingOperationCode.CREDENTIAL_ACCESS;
import static io.pivotal.security.audit.AuditingOperationCode.CREDENTIAL_UPDATE;
import static io.pivotal.security.audit.EventAuditRecordParametersFactory.createPermissionsEventAuditParameters;
import static io.pivotal.security.request.PermissionOperation.DELETE;
import static io.pivotal.security.request.PermissionOperation.READ;
import static io.pivotal.security.request.PermissionOperation.WRITE;
import static io.pivotal.security.request.PermissionOperation.WRITE_ACL;

@Service
public class PermissionedCredentialService {
  private final CredentialVersionDataService credentialVersionDataService;

  private PermissionService permissionService;
  private final CredentialFactory credentialFactory;
  private PermissionCheckingService permissionCheckingService;

  @Autowired
  public PermissionedCredentialService(
      CredentialVersionDataService credentialVersionDataService,
      PermissionService permissionService,
      CredentialFactory credentialFactory,
      PermissionCheckingService permissionCheckingService) {
    this.credentialVersionDataService = credentialVersionDataService;
    this.permissionService = permissionService;
    this.credentialFactory = credentialFactory;
    this.permissionCheckingService = permissionCheckingService;
  }

  public CredentialView save(
      String credentialName,
      String type,
      CredentialValue credentialValue,
      GenerationParameters generationParameters,
      List<PermissionEntry> accessControlEntries,
      boolean isOverwrite,
      UserContext userContext,
      PermissionEntry currentUserPermissionEntry,
      List<EventAuditRecordParameters> auditRecordParameters
  ) {
    CredentialVersion existingCredentialVersion = credentialVersionDataService.findMostRecent(credentialName);

    boolean shouldWriteNewEntity = existingCredentialVersion == null || isOverwrite;

    AuditingOperationCode credentialOperationCode =
        shouldWriteNewEntity ? CREDENTIAL_UPDATE : CREDENTIAL_ACCESS;
    auditRecordParameters
        .add(new EventAuditRecordParameters(credentialOperationCode, credentialName));

    if (existingCredentialVersion != null) {
      verifyCredentialWritePermission(userContext, credentialName);
    }

    if (existingCredentialVersion != null && accessControlEntries.size() > 0) {
      verifyAclWrite(userContext, credentialName);
    }

    if (existingCredentialVersion != null && !existingCredentialVersion.getCredentialType().equals(type)) {
      throw new ParameterizedValidationException("error.type_mismatch");
    }

    for (PermissionEntry accessControlEntry : accessControlEntries) {
      if (!permissionCheckingService.userAllowedToOperateOnActor(userContext, accessControlEntry.getActor())) {
        throw new InvalidAclOperationException("error.acl.invalid_update_operation");
      }
    }

    CredentialVersion credentialVersionToStore = existingCredentialVersion;
    if (shouldWriteNewEntity) {
      if (existingCredentialVersion == null) {
        accessControlEntries.add(currentUserPermissionEntry);
      }

      CredentialVersion newVersion = credentialFactory.makeNewCredentialVersion(
          CredentialType.valueOf(type),
          credentialName,
          credentialValue,
          existingCredentialVersion,
          generationParameters);
      credentialVersionToStore = credentialVersionDataService.save(newVersion);

      permissionService.saveAccessControlEntries(
          userContext,
          credentialVersionToStore.getCredential(),
          accessControlEntries);
      auditRecordParameters.addAll(createPermissionsEventAuditParameters(
          ACL_UPDATE,
          credentialVersionToStore.getName(),
          accessControlEntries
      ));
    }

    return CredentialView.fromEntity(credentialVersionToStore);
  }

  private void verifyCredentialWritePermission(UserContext userContext, String credentialName) {
    if (!permissionCheckingService
        .hasPermission(userContext.getAclUser(), credentialName, WRITE)) {
      throw new PermissionException("error.credential.invalid_access");
    }
  }

  private void verifyAclWrite(UserContext userContext, String credentialName) {
    if (!permissionCheckingService
        .hasPermission(userContext.getAclUser(), credentialName, WRITE_ACL)) {
      throw new PermissionException("error.credential.invalid_access");
    }
  }

  public boolean delete(UserContext userContext, String credentialName) {
    if (!permissionCheckingService
        .hasPermission(userContext.getAclUser(), credentialName, DELETE)) {
      throw new EntryNotFoundException("error.credential.invalid_access");
    }
    return credentialVersionDataService.delete(credentialName);
  }

  public List<CredentialVersion> findAllByName(UserContext userContext, String credentialName) {
    if (!permissionCheckingService
        .hasPermission(userContext.getAclUser(), credentialName, READ)) {
      throw new EntryNotFoundException("error.credential.invalid_access");
    }

    return credentialVersionDataService.findAllByName(credentialName);
  }

  public List<CredentialVersion> findNByName(UserContext userContext, String credentialName, Integer numberOfVersions) {
    if (!permissionCheckingService
        .hasPermission(userContext.getAclUser(), credentialName, READ)) {
      throw new EntryNotFoundException("error.credential.invalid_access");
    }

    return credentialVersionDataService.findNByName(credentialName, numberOfVersions);
  }

  public CredentialVersion findByUuid(UserContext userContext, String credentialUUID, List<EventAuditRecordParameters> auditRecordParametersList) {
    EventAuditRecordParameters eventAuditRecordParameters = new EventAuditRecordParameters(
        AuditingOperationCode.CREDENTIAL_ACCESS
    );
    auditRecordParametersList.add(eventAuditRecordParameters);

    CredentialVersion credentialVersion = credentialVersionDataService.findByUuid(credentialUUID);
    if (credentialVersion == null) {
      throw new EntryNotFoundException("error.credential.invalid_access");
    }
    String credentialName = credentialVersion.getName();
    eventAuditRecordParameters.setCredentialName(credentialName);

    if (!permissionCheckingService
        .hasPermission(userContext.getAclUser(), credentialName, READ)) {
      throw new EntryNotFoundException("error.credential.invalid_access");
    }
    return credentialVersionDataService.findByUuid(credentialUUID);
  }

  public List<String> findAllCertificateCredentialsByCaName(UserContext userContext, String caName) {
    if (!permissionCheckingService
        .hasPermission(userContext.getAclUser(), caName, PermissionOperation.READ)) {
      throw new EntryNotFoundException("error.credential.invalid_access");
    }

    return credentialVersionDataService.findAllCertificateCredentialsByCaName(caName);
  }

  public List<FindCredentialResult> findStartingWithPath(String path) {
    return credentialVersionDataService.findStartingWithPath(path);
  }

  public List<String> findAllPaths() {
    return credentialVersionDataService.findAllPaths();
  }

  public List<FindCredentialResult> findContainingName(String name) {
    return credentialVersionDataService.findContainingName(name);
  }

  public CredentialVersion findMostRecent(String credentialName) {
    return credentialVersionDataService.findMostRecent(credentialName);
  }
}
