package io.pivotal.security.integration;

import io.pivotal.security.CredentialManagerApp;
import io.pivotal.security.helper.AuditingHelper;
import io.pivotal.security.repository.EventAuditRecordRepository;
import io.pivotal.security.repository.RequestAuditRecordRepository;
import io.pivotal.security.util.DatabaseProfileResolver;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import static io.pivotal.security.util.AuthConstants.UAA_OAUTH2_PASSWORD_GRANT_TOKEN;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@ActiveProfiles(value = "unit-test", resolver = DatabaseProfileResolver.class)
@SpringBootTest(classes = CredentialManagerApp.class)
@Transactional
@TestPropertySource(properties = "security.authorization.acls.enabled=true")
public class RegenerationByCertificateAuthorityTest {

  private static final String API_V1_DATA_ENDPOINT = "/api/v1/data";
  private static final String API_V1_REGENERATE_ENDPOINT = "/api/v1/regenerate";

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private RequestAuditRecordRepository requestAuditRecordRepository;

  @Autowired
  private EventAuditRecordRepository eventAuditRecordRepository;

  private MockMvc mockMvc;
  private String originalCA;
  private String otherCA;
  private AuditingHelper auditingHelper;

  @Before
  public void beforeEach() throws Exception {
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();
    auditingHelper = new AuditingHelper(requestAuditRecordRepository, eventAuditRecordRepository);

    MockHttpServletRequestBuilder generateCAToRotateRequest = post(API_V1_DATA_ENDPOINT)
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        //language=JSON
        .content("{\n"
            + "  \"name\" : \"/ca-to-rotate\",\n"
            + "  \"type\" : \"certificate\",\n"
            + "  \"parameters\" : {\n"
            + "\"is_ca\": true,\n"
            + "\"common_name\": \"original ca\"\n"
            + "}}");

    String generateCAResult = this.mockMvc.perform(generateCAToRotateRequest)
        .andDo(print())
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    originalCA= (new JSONObject(generateCAResult)).getString("value");

    MockHttpServletRequestBuilder generateOtherCARequest = post(API_V1_DATA_ENDPOINT)
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        //language=JSON
        .content("{\n"
            + "  \"name\" : \"/other-ca\",\n"
            + "  \"type\" : \"certificate\",\n"
            + "  \"parameters\" : {\n"
            + "\"is_ca\": true,\n"
            + "\"common_name\": \"other ca\"\n"
            + "}}");

    String otherCAResult = this.mockMvc.perform(generateOtherCARequest)
        .andDo(print())
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    otherCA= (new JSONObject(otherCAResult)).getString("value");

    MockHttpServletRequestBuilder generateCertSignedByOriginalCARequest = post(API_V1_DATA_ENDPOINT)
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        //language=JSON
        .content("{\n"
            + "  \"name\" : \"/cert-to-regenerate\",\n"
            + "  \"type\" : \"certificate\",\n"
            + "  \"parameters\" : {\n"
            + "\"ca\": \"/ca-to-rotate\",\n"
            + "\"common_name\": \"cert to regenerate\"\n"
            + "}}");

    String certSignedByOriginalCAResult = this.mockMvc.perform(generateCertSignedByOriginalCARequest)
        .andDo(print())
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat((new JSONObject(certSignedByOriginalCAResult)).getString("value"), notNullValue());

    MockHttpServletRequestBuilder generateCertSignedByOtherCARequest = post(API_V1_DATA_ENDPOINT)
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        //language=JSON
        .content("{\n"
            + "  \"name\" : \"/cert-not-to-regenerate\",\n"
            + "  \"type\" : \"certificate\",\n"
            + "  \"parameters\" : {\n"
            + "\"ca\": \"/other-ca\",\n"
            + "\"common_name\": \"cert not to regenerate\"\n"
            + "}}");

    String certSignedByOtherCAResult = this.mockMvc.perform(generateCertSignedByOtherCARequest)
        .andDo(print())
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat((new JSONObject(certSignedByOtherCAResult)).getString("value"), notNullValue());
  }

  @Test
  public void regeneratingCertificatesSignedByCA_shouldRegenerateCertificates() throws Exception {
    MockHttpServletRequestBuilder regenerateCertificatesRequest = post(API_V1_REGENERATE_ENDPOINT)
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        //language=JSON
        .content("{\n"
            + "  \"signed_by\" : \"/ca-to-rotate\"\n"
            + "}");

    String regenerateCertificatesResult = this.mockMvc.perform(regenerateCertificatesRequest)
        .andDo(print())
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

//    String regeneratedPassword = (new JSONObject(regenerateCertificatesResult)).getString("value");
//
//    assertThat(regeneratedPassword, notNullValue());
//    assertThat(regeneratedPassword, not(equalTo(originalCA)));
  }

//  @Test
//  public void regenerating_PersistsAnAuditEntry() throws Exception {
//    MockHttpServletRequestBuilder request = post(API_V1_REGENERATE_ENDPOINT)
//        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
//        .accept(APPLICATION_JSON)
//        .contentType(APPLICATION_JSON)
//        .content("{\"name\":\"picard\"}");
//
//    mockMvc.perform(request)
//        .andExpect(status().isOk())
//        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
//        .andExpect(jsonPath("$.type").value("password"));
//
//    auditingHelper.verifyAuditing(CREDENTIAL_UPDATE, "/picard", "uaa-user:df0c1a26-2875-4bf5-baf9-716c6bb5ea6d", API_V1_REGENERATE_ENDPOINT, 200);
//  }
}
