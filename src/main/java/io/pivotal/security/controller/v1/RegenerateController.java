package io.pivotal.security.controller.v1;

import io.pivotal.security.audit.EventAuditLogService;
import io.pivotal.security.audit.RequestUuid;
import io.pivotal.security.auth.UserContext;
import io.pivotal.security.request.PermissionEntry;
import io.pivotal.security.request.RegenerateRequest;
import io.pivotal.security.service.RegenerateService;
import io.pivotal.security.view.CredentialView;
import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

@RestController
@RequestMapping(
    path = RegenerateController.API_V1_REGENERATE,
    produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
public class RegenerateController {
  static final String API_V1_REGENERATE = "api/v1/regenerate";

  private static final Logger LOGGER = LogManager.getLogger(RegenerateController.class);
  private final EventAuditLogService eventAuditLogService;
  private RegenerateService regenerateService;

  @Autowired
  public RegenerateController(
      RegenerateService regenerateService,
      EventAuditLogService eventAuditLogService
  ) {
    this.regenerateService = regenerateService;
    this.eventAuditLogService = eventAuditLogService;
  }

  @RequestMapping(path = "", method = RequestMethod.POST)
  @ResponseStatus(HttpStatus.OK)
  public CredentialView regenerate(
      UserContext userContext,
      RequestUuid requestUuid,
      PermissionEntry currentUserPermissionEntry,
      @RequestBody RegenerateRequest requestBody
  ) throws IOException {
    return eventAuditLogService
        .auditEvents(requestUuid, userContext, (auditRecordParameters -> {
          if (StringUtils.isEmpty(requestBody.getName())) {
            return regenerateService
                .performRegenerateBySigner(requestBody.getSignedBy(), userContext,
                    currentUserPermissionEntry, auditRecordParameters);
          } else {
            return regenerateService
                .performRegenerateByName(requestBody.getName(), userContext,
                    currentUserPermissionEntry, auditRecordParameters);
          }
        }));
  }
}
