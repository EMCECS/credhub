package io.pivotal.security.controller.v1.permissions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greghaskins.spectrum.Spectrum;
import io.pivotal.security.handler.AccessControlHandler;
import io.pivotal.security.helper.JsonHelper;
import io.pivotal.security.service.AuditLogService;
import io.pivotal.security.service.AuditRecordBuilder;
import io.pivotal.security.util.ExceptionThrowingFunction;
import io.pivotal.security.view.AccessControlListResponse;
import org.junit.runner.RunWith;
import org.springframework.http.RequestEntity;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static com.google.common.collect.Lists.newArrayList;
import static com.greghaskins.spectrum.Spectrum.beforeEach;
import static com.greghaskins.spectrum.Spectrum.describe;
import static com.greghaskins.spectrum.Spectrum.it;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(Spectrum.class)
public class AccessControlListControllerTest {

  private AccessControlHandler accessControlHandler;
  private MockMvc mockMvc;
  private AuditLogService auditLogService;
  private AccessControlListController subject;

  {
    beforeEach(() -> {
      accessControlHandler = mock(AccessControlHandler.class);
      auditLogService = mock(AuditLogService.class);

      subject = new AccessControlListController(accessControlHandler, auditLogService);

      MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter =
        new MappingJackson2HttpMessageConverter();
      ObjectMapper objectMapper = JsonHelper.createObjectMapper();
      mappingJackson2HttpMessageConverter.setObjectMapper(objectMapper);
      mockMvc = MockMvcBuilders.standaloneSetup(subject)
        .setMessageConverters(mappingJackson2HttpMessageConverter)
        .build();
    });

    describe("/acls", () -> {
      describe("#GET", () -> {
        it("should return the ACL for the credential", () -> {
          AccessControlListResponse accessControlListResponse = new AccessControlListResponse(
              "test_credential_name", newArrayList());
          when(accessControlHandler.getAccessControlListResponse("test_credential_name"))
              .thenReturn(accessControlListResponse);

          when(auditLogService.performWithAuditing(any())).thenAnswer(answer -> {
            ExceptionThrowingFunction<AuditRecordBuilder, RequestEntity, Exception> block
              = answer.getArgumentAt(0, ExceptionThrowingFunction.class);
            return block.apply(new AuditRecordBuilder());
          });

          mockMvc.perform(get("/api/v1/acls?credential_name=test_credential_name"))
              .andExpect(status().isOk())
              .andExpect(jsonPath("$.credential_name").value("test_credential_name"));
        });
      });
    });
  }
}
