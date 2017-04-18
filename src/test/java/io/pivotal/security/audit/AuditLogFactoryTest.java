package io.pivotal.security.audit;

import io.pivotal.security.auth.UserContext;
import io.pivotal.security.entity.EventAuditRecord;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.UUID;

import static io.pivotal.security.audit.AuditLogFactory.createEventAuditRecord;
import static io.pivotal.security.audit.AuditingOperationCode.CREDENTIAL_ACCESS;
import static io.pivotal.security.audit.AuditingOperationCode.CREDENTIAL_DELETE;
import static io.pivotal.security.audit.AuditingOperationCode.CREDENTIAL_UPDATE;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class AuditLogFactoryTest {
  @Test
  public void createEventAuditRecord_whenAllFieldsAreComplete_createsEventAuditRecord() {
    final UserContext userContext = mock(UserContext.class);
    final UUID requestUuid = UUID.randomUUID();

    final EventAuditRecordParameters eventAuditRecordParameters = new EventAuditRecordParameters();
    eventAuditRecordParameters.setAuditingOperationCode(CREDENTIAL_ACCESS);
    eventAuditRecordParameters.setCredentialName("/test-credential");
    eventAuditRecordParameters.setAceOperation(CREDENTIAL_DELETE);
    eventAuditRecordParameters.setAceActor("ace-actor");

    when(userContext.getAclUser()).thenReturn("test-actor");

    EventAuditRecord eventAuditRecord = createEventAuditRecord(
        eventAuditRecordParameters,
        userContext,
        requestUuid,
        true
    );

    assertThat(eventAuditRecord.getOperation(), equalTo("credential_access"));
    assertThat(eventAuditRecord.getCredentialName(), equalTo("/test-credential"));
    assertThat(eventAuditRecord.getActor(), equalTo("test-actor"));
    assertThat(eventAuditRecord.getRequestUuid(), equalTo(requestUuid));
    assertThat(eventAuditRecord.isSuccess(), equalTo(true));
    assertThat(eventAuditRecord.getAceOperation(), equalTo("credential_delete"));
    assertThat(eventAuditRecord.getAceActor(), equalTo("ace-actor"));
  }

  @Test
  public void createEventAuditRecord_whenOperationIsNull_fallsBackToUnknownOperation() {
    final UserContext userContext = mock(UserContext.class);
    final UUID requestUuid = UUID.randomUUID();

    final EventAuditRecordParameters eventAuditRecordParameters = new EventAuditRecordParameters();
    eventAuditRecordParameters.setCredentialName("/test-credential");

    when(userContext.getAclUser()).thenReturn("test-actor");

    EventAuditRecord eventAuditRecord = createEventAuditRecord(
        eventAuditRecordParameters,
        userContext,
        requestUuid,
        true
    );

    assertThat(eventAuditRecord.getOperation(), equalTo("unknown_operation"));
  }

  @Test
  public void createEventAuditRecord_whenParameterAceOperationIsNull_createsEventAuditRecord() {
    final UserContext userContext = mock(UserContext.class);
    final UUID requestUuid = UUID.randomUUID();

    final EventAuditRecordParameters eventAuditRecordParameters = new EventAuditRecordParameters();
    eventAuditRecordParameters.setCredentialName("/test-credential");

    when(userContext.getAclUser()).thenReturn("test-actor");

    EventAuditRecord eventAuditRecord = createEventAuditRecord(
        eventAuditRecordParameters,
        userContext,
        requestUuid,
        true
    );

    assertThat(eventAuditRecord.getAceOperation(), equalTo(null));
  }

  @Test
  public void createEventAuditRecord_whenCredentialNameIsNull_createsEventAuditRecord() {
    final UserContext userContext = mock(UserContext.class);
    final UUID requestUuid = UUID.randomUUID();

    final EventAuditRecordParameters eventAuditRecordParameters = new EventAuditRecordParameters();
    eventAuditRecordParameters.setAuditingOperationCode(CREDENTIAL_UPDATE);

    when(userContext.getAclUser()).thenReturn("test-actor");

    EventAuditRecord eventAuditRecord = createEventAuditRecord(
        eventAuditRecordParameters,
        userContext,
        requestUuid,
        true
    );

    assertNotNull(eventAuditRecord);
  }

  @Test
  public void createEventAuditRecord_whenParametersAreNull_createsEventAuditRecord() {
    final UserContext userContext = mock(UserContext.class);
    final UUID requestUuid = UUID.randomUUID();

    when(userContext.getAclUser()).thenReturn("test-actor");

    EventAuditRecord eventAuditRecord = createEventAuditRecord(
        null,
        userContext,
        requestUuid,
        true
    );

    assertNotNull(eventAuditRecord);
  }
}
