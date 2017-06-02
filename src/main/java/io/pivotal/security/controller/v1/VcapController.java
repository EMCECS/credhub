package io.pivotal.security.controller.v1;

import com.jayway.jsonpath.DocumentContext;
import io.pivotal.security.audit.EventAuditLogService;
import io.pivotal.security.audit.RequestUuid;
import io.pivotal.security.auth.UserContext;
import io.pivotal.security.data.CredentialVersionDataService;
import io.pivotal.security.service.JsonInterpolationService;
import org.apache.logging.log4j.core.util.IOUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.io.InputStream;
import java.io.InputStreamReader;

@SuppressWarnings("unused")
@RestController
@RequestMapping(path = VcapController.API_V1, produces = MediaType.APPLICATION_JSON_UTF8_VALUE)
public class VcapController {

  static final String API_V1 = "/api/v1";
  private final CredentialVersionDataService credentialVersionDataService;
  private final JsonInterpolationService jsonInterpolationService;
  private final EventAuditLogService eventAuditLogService;

  @Autowired
  VcapController(
      JsonInterpolationService jsonInterpolationService,
      CredentialVersionDataService credentialVersionDataService,
      EventAuditLogService eventAuditLogService) {
    this.jsonInterpolationService = jsonInterpolationService;
    this.credentialVersionDataService = credentialVersionDataService;
    this.eventAuditLogService = eventAuditLogService;
  }

  @RequestMapping(method = RequestMethod.POST, path = "/vcap")
  @ResponseStatus(HttpStatus.OK)
  public String interpolate(InputStream requestBody,
      HttpServletRequest request,
      Authentication authentication,
      RequestUuid requestUuid,
      UserContext userContext) throws Exception {
    String requestAsString = IOUtils.toString(new InputStreamReader(requestBody));

    return eventAuditLogService.auditEvents(requestUuid, userContext, (eventAuditRecordParameters -> {
      DocumentContext responseJson;
      try {
        responseJson = jsonInterpolationService
            .interpolateCredhubReferences(requestAsString, credentialVersionDataService,
                eventAuditRecordParameters);
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
      return responseJson.jsonString();
    }));
  }
}
