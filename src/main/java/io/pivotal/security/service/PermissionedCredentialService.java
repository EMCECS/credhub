package io.pivotal.security.service;

import io.pivotal.security.audit.AuditingOperationCode;
import io.pivotal.security.audit.EventAuditRecordParameters;
import io.pivotal.security.auth.UserContextHolder;
import io.pivotal.security.constants.CredentialType;
import io.pivotal.security.credential.CredentialValue;
import io.pivotal.security.data.CertificateAuthorityService;
import io.pivotal.security.data.CredentialVersionDataService;
import io.pivotal.security.domain.CertificateCredentialVersion;
import io.pivotal.security.domain.CredentialFactory;
import io.pivotal.security.domain.CredentialVersion;
import io.pivotal.security.exceptions.EntryNotFoundException;
import io.pivotal.security.exceptions.InvalidQueryParameterException;
import io.pivotal.security.exceptions.ParameterizedValidationException;
import io.pivotal.security.exceptions.PermissionException;
import io.pivotal.security.request.GenerationParameters;
import io.pivotal.security.request.PermissionEntry;
import io.pivotal.security.request.PermissionOperation;
import io.pivotal.security.view.FindCredentialResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

import static io.pivotal.security.audit.AuditingOperationCode.CREDENTIAL_ACCESS;
import static io.pivotal.security.audit.AuditingOperationCode.CREDENTIAL_DELETE;
import static io.pivotal.security.audit.AuditingOperationCode.CREDENTIAL_FIND;
import static io.pivotal.security.audit.AuditingOperationCode.CREDENTIAL_UPDATE;
import static io.pivotal.security.request.PermissionOperation.DELETE;
import static io.pivotal.security.request.PermissionOperation.READ;
import static io.pivotal.security.request.PermissionOperation.WRITE;
import static io.pivotal.security.request.PermissionOperation.WRITE_ACL;

@Service
public class PermissionedCredentialService {
  private final CredentialVersionDataService credentialVersionDataService;

  private final CredentialFactory credentialFactory;
  private final CertificateAuthorityService certificateAuthorityService;
  private PermissionCheckingService permissionCheckingService;
  private final UserContextHolder userContextHolder;

  @Autowired
  public PermissionedCredentialService(
      CredentialVersionDataService credentialVersionDataService,
      CredentialFactory credentialFactory,
      PermissionCheckingService permissionCheckingService,
      CertificateAuthorityService certificateAuthorityService,
      UserContextHolder userContextHolder) {
    this.credentialVersionDataService = credentialVersionDataService;
    this.credentialFactory = credentialFactory;
    this.permissionCheckingService = permissionCheckingService;
    this.certificateAuthorityService = certificateAuthorityService;
    this.userContextHolder = userContextHolder;
  }

  public CredentialVersion save(
      CredentialVersion existingCredentialVersion, String credentialName,
      String type,
      CredentialValue credentialValue,
      GenerationParameters generationParameters,
      List<PermissionEntry> accessControlEntries,
      String overwriteMode,
      List<EventAuditRecordParameters> auditRecordParameters,
      boolean transitional) {
    final boolean isNewCredential = existingCredentialVersion == null;

    boolean shouldWriteNewCredential = shouldWriteNewCredential(existingCredentialVersion, generationParameters, overwriteMode, isNewCredential);

    writeSaveAuditRecord(credentialName, auditRecordParameters, shouldWriteNewCredential);

    validateCredentialSave(credentialName, type, accessControlEntries, existingCredentialVersion);

    if (!shouldWriteNewCredential) {
      return existingCredentialVersion;
    }

    return makeAndSaveNewCredential(
        credentialName,
        type,
        credentialValue,
        generationParameters,
        existingCredentialVersion,
        transitional);
  }

  public boolean delete(String credentialName, List<EventAuditRecordParameters> auditRecordParameters) {
    auditRecordParameters.add(new EventAuditRecordParameters(CREDENTIAL_DELETE, credentialName));
    if (!permissionCheckingService
        .hasPermission(userContextHolder.getUserContext().getActor(), credentialName, DELETE)) {
      throw new EntryNotFoundException("error.credential.invalid_access");
    }
    return credentialVersionDataService.delete(credentialName);
  }

  public List<CredentialVersion> findAllByName(String credentialName, List<EventAuditRecordParameters> auditRecordParametersList) {
    auditRecordParametersList.add(new EventAuditRecordParameters(CREDENTIAL_ACCESS, credentialName));

    if (!permissionCheckingService
        .hasPermission(userContextHolder.getUserContext().getActor(), credentialName, READ)) {
      throw new EntryNotFoundException("error.credential.invalid_access");
    }

    return credentialVersionDataService.findAllByName(credentialName);
  }

  public List<CredentialVersion> findNByName(String credentialName, Integer numberOfVersions, List<EventAuditRecordParameters> auditRecordParametersList) {
    auditRecordParametersList.add(new EventAuditRecordParameters(CREDENTIAL_ACCESS, credentialName));

    if (numberOfVersions < 0) {
      throw new InvalidQueryParameterException("error.invalid_query_parameter", "versions");
    }

    if (!permissionCheckingService
        .hasPermission(userContextHolder.getUserContext().getActor(), credentialName, READ)) {
      throw new EntryNotFoundException("error.credential.invalid_access");
    }

    return credentialVersionDataService.findNByName(credentialName, numberOfVersions);
  }

  public CredentialVersion findByUuid(String credentialUUID, List<EventAuditRecordParameters> auditRecordParameters) {
    EventAuditRecordParameters eventAuditRecordParameters = new EventAuditRecordParameters(
        AuditingOperationCode.CREDENTIAL_ACCESS
    );
    auditRecordParameters.add(eventAuditRecordParameters);

    CredentialVersion credentialVersion = credentialVersionDataService.findByUuid(credentialUUID);
    if (credentialVersion == null) {
      throw new EntryNotFoundException("error.credential.invalid_access");
    }
    String credentialName = credentialVersion.getName();
    eventAuditRecordParameters.setCredentialName(credentialName);

    if (!permissionCheckingService
        .hasPermission(userContextHolder.getUserContext().getActor(), credentialName, READ)) {
      throw new EntryNotFoundException("error.credential.invalid_access");
    }
    return credentialVersionDataService.findByUuid(credentialUUID);
  }

  public List<String> findAllCertificateCredentialsByCaName(String caName) {
    if (!permissionCheckingService
        .hasPermission(userContextHolder.getUserContext().getActor(), caName, PermissionOperation.READ)) {
      throw new EntryNotFoundException("error.credential.invalid_access");
    }

    return credentialVersionDataService.findAllCertificateCredentialsByCaName(caName);
  }

  public List<FindCredentialResult> findStartingWithPath(String path, List<EventAuditRecordParameters> auditRecordParameters) {
    auditRecordParameters.add(new EventAuditRecordParameters(CREDENTIAL_FIND));
    return credentialVersionDataService.findStartingWithPath(path);
  }

  public List<String> findAllPaths(List<EventAuditRecordParameters> auditRecordParameters) {
    auditRecordParameters.add(new EventAuditRecordParameters(CREDENTIAL_FIND));
    return credentialVersionDataService.findAllPaths();
  }

  public List<FindCredentialResult> findContainingName(String name, List<EventAuditRecordParameters> auditRecordParameters) {
    auditRecordParameters.add(new EventAuditRecordParameters(CREDENTIAL_FIND));
    return credentialVersionDataService.findContainingName(name);
  }

  public CredentialVersion findMostRecent(String credentialName) {
    return credentialVersionDataService.findMostRecent(credentialName);
  }

  private CredentialVersion makeAndSaveNewCredential(String credentialName, String type, CredentialValue credentialValue, GenerationParameters generationParameters, CredentialVersion existingCredentialVersion, boolean transitional) {
    CredentialVersion newVersion = credentialFactory.makeNewCredentialVersion(
        CredentialType.valueOf(type),
        credentialName,
        credentialValue,
        existingCredentialVersion,
        generationParameters, transitional);
    return credentialVersionDataService.save(newVersion);
  }

  private boolean shouldWriteNewCredential(CredentialVersion existingCredentialVersion, GenerationParameters generationParameters, String overwriteMode, boolean isNewCredential) {
    boolean shouldWriteNewCredential;
    if (isNewCredential) {
      shouldWriteNewCredential = true;
    } else if ("converge".equals(overwriteMode)) {
      if (existingCredentialVersion instanceof CertificateCredentialVersion) {
        final CertificateCredentialVersion certificateCredentialVersion = (CertificateCredentialVersion) existingCredentialVersion;
        if (certificateCredentialVersion.getCaName() != null) {
          boolean updatedCA = !certificateCredentialVersion.getCa().equals(certificateAuthorityService.findMostRecent(certificateCredentialVersion.getCaName()).getCertificate());
          if (updatedCA) {
            return true;
          }
        }
      }
      shouldWriteNewCredential = !existingCredentialVersion.matchesGenerationParameters(generationParameters);
    } else {
      shouldWriteNewCredential = "overwrite".equals(overwriteMode);
    }
    return shouldWriteNewCredential;
  }

  private void validateCredentialSave(String credentialName, String type, List<PermissionEntry> accessControlEntries, CredentialVersion existingCredentialVersion) {
    if (existingCredentialVersion != null) {
      verifyCredentialWritePermission(credentialName);
    }

    if (existingCredentialVersion != null && accessControlEntries.size() > 0) {
      verifyWritePermission(credentialName);
    }

    if (existingCredentialVersion != null && !existingCredentialVersion.getCredentialType().equals(type)) {
      throw new ParameterizedValidationException("error.type_mismatch");
    }
  }

  private void writeSaveAuditRecord(String credentialName, List<EventAuditRecordParameters> auditRecordParameters, boolean shouldWriteNewEntity) {
    AuditingOperationCode credentialOperationCode = shouldWriteNewEntity ? CREDENTIAL_UPDATE : CREDENTIAL_ACCESS;
    auditRecordParameters.add(new EventAuditRecordParameters(credentialOperationCode, credentialName));
  }

  private void verifyCredentialWritePermission(String credentialName) {
    if (!permissionCheckingService.hasPermission(userContextHolder.getUserContext().getActor(), credentialName, WRITE)) {
      throw new PermissionException("error.credential.invalid_access");
    }
  }

  private void verifyWritePermission(String credentialName) {
    if (!permissionCheckingService.hasPermission(userContextHolder.getUserContext().getActor(), credentialName, WRITE_ACL)) {
      throw new PermissionException("error.credential.invalid_access");
    }
  }
}
