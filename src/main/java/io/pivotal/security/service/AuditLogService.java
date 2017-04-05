package io.pivotal.security.service;

import io.pivotal.security.data.OperationAuditRecordDataService;
import io.pivotal.security.entity.OperationAuditRecord;
import io.pivotal.security.util.CurrentTimeProvider;
import io.pivotal.security.util.ExceptionThrowingFunction;
import io.pivotal.security.view.ResponseError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.provider.token.ResourceServerTokenServices;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import javax.annotation.PostConstruct;

@Service
public class AuditLogService {

  private final CurrentTimeProvider currentTimeProvider;
  private final ResourceServerTokenServices tokenServices;
  private final OperationAuditRecordDataService operationAuditRecordDataService;
  private final PlatformTransactionManager transactionManager;
  private final MessageSource messageSource;
  private final SecurityEventsLogService securityEventsLogService;
  private MessageSourceAccessor messageSourceAccessor;

  @Autowired
  AuditLogService(
      CurrentTimeProvider currentTimeProvider,
      ResourceServerTokenServices tokenServices,
      OperationAuditRecordDataService operationAuditRecordDataService,
      PlatformTransactionManager transactionManager,
      MessageSource messageSource,
      SecurityEventsLogService securityEventsLogService
  ) {
    this.currentTimeProvider = currentTimeProvider;
    this.tokenServices = tokenServices;
    this.operationAuditRecordDataService = operationAuditRecordDataService;
    this.transactionManager = transactionManager;
    this.messageSource = messageSource;
    this.securityEventsLogService = securityEventsLogService;
  }

  @PostConstruct
  public void init() {
    messageSourceAccessor = new MessageSourceAccessor(messageSource);
  }

  @SuppressWarnings("ReturnInsideFinallyBlock")
  public ResponseEntity<?> performWithAuditing(
      ExceptionThrowingFunction<AuditRecordBuilder, ResponseEntity<?>, Throwable> respondToRequestFunction
  ) throws Throwable {
    AuditRecordBuilder auditRecordBuilder = new AuditRecordBuilder();

    ResponseEntity<?> responseEntity = new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    boolean responseSucceeded = false;

    TransactionStatus transaction =
        transactionManager.getTransaction(new DefaultTransactionDefinition());
    try {
      responseEntity = respondToRequestFunction.apply(auditRecordBuilder);
      responseSucceeded = responseEntity.getStatusCode().is2xxSuccessful();
    } finally {
      try {
        if (!responseSucceeded) {
          transactionManager.rollback(transaction);
          transaction = transactionManager.getTransaction(new DefaultTransactionDefinition());
        }
        auditRecordBuilder.setIsSuccess(responseSucceeded);

        OperationAuditRecord auditRecord = writeAuditRecord(auditRecordBuilder, responseEntity);
        transactionManager.commit(transaction);
        securityEventsLogService.log(auditRecord);
      } catch (Exception e) {
        if (!transaction.isCompleted()) {
          transactionManager.rollback(transaction);
        }
        ResponseError error = new ResponseError(
            messageSourceAccessor.getMessage("error.audit_save_failure"));
        return new ResponseEntity<>(error, HttpStatus.INTERNAL_SERVER_ERROR);
      }
    }

    return responseEntity;
  }

  private OperationAuditRecord writeAuditRecord(
      AuditRecordBuilder auditRecordBuilder,
      ResponseEntity<?> responseEntity
  ) throws Exception {
    OperationAuditRecord auditRecord = getOperationAuditRecord(auditRecordBuilder,
        responseEntity.getStatusCodeValue());
    operationAuditRecordDataService.save(auditRecord);
    return auditRecord;
  }

  private OperationAuditRecord getOperationAuditRecord(AuditRecordBuilder auditRecordBuilder,
                                                       int statusCode) throws Exception {
    return auditRecordBuilder
        .setRequestStatus(statusCode)
        .build(currentTimeProvider.getInstant(), tokenServices);
  }

}
