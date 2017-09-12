package io.pivotal.security.integration;

import io.pivotal.security.CredentialManagerApp;
import io.pivotal.security.audit.EventAuditRecordParameters;
import io.pivotal.security.data.CredentialDataService;
import io.pivotal.security.helper.AuditingHelper;
import io.pivotal.security.repository.EventAuditRecordRepository;
import io.pivotal.security.repository.RequestAuditRecordRepository;
import io.pivotal.security.util.DatabaseProfileResolver;
import org.hamcrest.core.IsEqual;
import org.json.JSONArray;
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

import java.util.Arrays;
import java.util.List;

import static com.google.common.collect.Lists.newArrayList;
import static io.pivotal.security.audit.AuditingOperationCode.CREDENTIAL_UPDATE;
import static io.pivotal.security.util.AuthConstants.UAA_OAUTH2_CLIENT_CREDENTIALS_ACTOR_ID;
import static io.pivotal.security.util.AuthConstants.UAA_OAUTH2_CLIENT_CREDENTIALS_TOKEN;
import static io.pivotal.security.util.AuthConstants.UAA_OAUTH2_PASSWORD_GRANT_ACTOR_ID;
import static io.pivotal.security.util.AuthConstants.UAA_OAUTH2_PASSWORD_GRANT_TOKEN;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@ActiveProfiles(value = "unit-test", resolver = DatabaseProfileResolver.class)
@SpringBootTest(classes = CredentialManagerApp.class)
@Transactional
@TestPropertySource(properties = "security.authorization.acls.enabled=true")
public class BulkRegenerateTest {

  private static final String API_V1_DATA_ENDPOINT = "/api/v1/data";
  private static final String API_V1_BULK_REGENERATE_ENDPOINT = "/api/v1/bulk-regenerate";
  private static final String API_V1_PERMISSION_ENDPOINT = "/api/v1/permissions";

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private RequestAuditRecordRepository requestAuditRecordRepository;

  @Autowired
  private EventAuditRecordRepository eventAuditRecordRepository;

  @Autowired
  private CredentialDataService credentialDataService;

  private MockMvc mockMvc;
  private AuditingHelper auditingHelper;

  @Before
  public void beforeEach() throws Exception {
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();
    auditingHelper = new AuditingHelper(requestAuditRecordRepository, eventAuditRecordRepository);

    generateCA("/ca-to-rotate", "original ca");
    generateCA("/other-ca", "other ca");

    generateSignedCertificate("/cert-to-regenerate", "cert to regenerate", "/ca-to-rotate");
    generateSignedCertificate("/cert-to-regenerate", "cert to regenerate", "/ca-to-rotate");
    generateSignedCertificate("/cert-to-regenerate-as-well", "cert to regenerate as well", "/ca-to-rotate");
    generateSignedCertificate("/cert-not-to-regenerate", "cert not to regenerate", "/other-ca");
  }

  @Test
  public void regeneratingCertificatesSignedByCA_shouldRegenerateCertificates() throws Exception {
    MockHttpServletRequestBuilder regenerateCertificatesRequest = post(API_V1_BULK_REGENERATE_ENDPOINT)
        .header("Authorization", "Bearer " + UAA_OAUTH2_CLIENT_CREDENTIALS_TOKEN)
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

    final JSONArray regeneratedCredentials = (new JSONObject(regenerateCertificatesResult)).getJSONArray("regenerated_credentials");
    final List<String> result = Arrays.asList(regeneratedCredentials.getString(0), regeneratedCredentials.getString(1));

    assertThat(regeneratedCredentials.length(), equalTo(2));
    assertThat(result, containsInAnyOrder("/cert-to-regenerate", "/cert-to-regenerate-as-well"));
  }

  @Test
  public void regenerating_PersistsAnAuditEntry() throws Exception {
    MockHttpServletRequestBuilder request = post(API_V1_BULK_REGENERATE_ENDPOINT)
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{\n"
            + "  \"signed_by\" : \"/ca-to-rotate\"\n"
            + "}");

    mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));

    auditingHelper.verifyAuditing(UAA_OAUTH2_PASSWORD_GRANT_ACTOR_ID, "/api/v1/bulk-regenerate", 200, newArrayList(
        new EventAuditRecordParameters(CREDENTIAL_UPDATE, "/cert-to-regenerate-as-well"),
        new EventAuditRecordParameters(CREDENTIAL_UPDATE, "/cert-to-regenerate")
    ));
  }

  @Test
  public void regeneratingCertificatesSignedByCA_whenUserCannotReadCa_shouldFailAndNotRotateAnyCertificates()
      throws Exception {
    //revoke read access to ca
    MockHttpServletRequestBuilder revokeCaReadAccess = delete(API_V1_PERMISSION_ENDPOINT + "?credential_name=/ca-to-rotate&actor=" + UAA_OAUTH2_CLIENT_CREDENTIALS_ACTOR_ID)
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON);

    mockMvc.perform(revokeCaReadAccess)
        .andExpect(status().isNoContent());

    assertThat(credentialDataService.findAllByName("/cert-to-regenerate").size(), equalTo(2));
    assertThat(credentialDataService.findAllByName("/cert-to-regenerate-as-well").size(), equalTo(1));

    MockHttpServletRequestBuilder regenerateCertificatesRequest = post(API_V1_BULK_REGENERATE_ENDPOINT)
        .header("Authorization", "Bearer " + UAA_OAUTH2_CLIENT_CREDENTIALS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        //language=JSON
        .content("{\n"
            + "  \"signed_by\" : \"/ca-to-rotate\"\n"
            + "}");

    mockMvc.perform(regenerateCertificatesRequest)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", IsEqual.equalTo("The request could not be completed because the credential does not exist or you do not have sufficient authorization.")));

    assertThat(credentialDataService.findAllByName("/cert-to-regenerate").size(), equalTo(2));
    assertThat(credentialDataService.findAllByName("/cert-to-regenerate-as-well").size(), equalTo(1));
  }

  @Test
  public void regeneratingCertificatesSignedByCA_whenUserCannotWriteToOneOfTheCertificates_shouldFailAndNotRotateAnyCertificates()
      throws Exception {
    //revoke write access to second certificate
    MockHttpServletRequestBuilder revokeWriteAccessRequest =
        delete(API_V1_PERMISSION_ENDPOINT + "?credential_name=/cert-to-regenerate&actor=" +
            UAA_OAUTH2_CLIENT_CREDENTIALS_ACTOR_ID)
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON);

    mockMvc.perform(revokeWriteAccessRequest)
        .andExpect(status().isNoContent());

    MockHttpServletRequestBuilder regenerateCertificatesRequest = post(API_V1_BULK_REGENERATE_ENDPOINT)
        .header("Authorization", "Bearer " + UAA_OAUTH2_CLIENT_CREDENTIALS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        //language=JSON
        .content("{\n"
            + "  \"signed_by\" : \"/ca-to-rotate\"\n"
            + "}");

    mockMvc.perform(regenerateCertificatesRequest)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", IsEqual.equalTo("The request could not be completed because the credential does not exist or you do not have sufficient authorization.")));

    assertThat(credentialDataService.findAllByName("/cert-to-regenerate").size(), equalTo(2));
    assertThat(credentialDataService.findAllByName("/cert-to-regenerate-as-well").size(), equalTo(1));
  }

  @Test
  public void regeneratingCertificatesSignedByCA_whenUserCannotWriteToAllOfTheCertificates_shouldFailAndNotRotateAnyCertificates()
      throws Exception {
    //revoke read access to ca
    MockHttpServletRequestBuilder revokeCaReadAccess = delete(API_V1_PERMISSION_ENDPOINT + "?credential_name=/cert-to-regenerate-as-well&actor=" + UAA_OAUTH2_CLIENT_CREDENTIALS_ACTOR_ID)
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON);

    mockMvc.perform(revokeCaReadAccess)
        .andExpect(status().isNoContent());

    MockHttpServletRequestBuilder regenerateCertificatesRequest = post(API_V1_BULK_REGENERATE_ENDPOINT)
        .header("Authorization", "Bearer " + UAA_OAUTH2_CLIENT_CREDENTIALS_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        //language=JSON
        .content("{\n"
            + "  \"signed_by\" : \"/ca-to-rotate\"\n"
            + "}");

    mockMvc.perform(regenerateCertificatesRequest)
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.error", IsEqual.equalTo("The request could not be completed because the credential does not exist or you do not have sufficient authorization.")));

    assertThat(credentialDataService.findAllByName("/cert-to-regenerate").size(), equalTo(2));
    assertThat(credentialDataService.findAllByName("/cert-to-regenerate-as-well").size(), equalTo(1));
  }

  private void generateCA(String caName, String caCommonName) throws Exception {
    MockHttpServletRequestBuilder generateCAToRotateRequest = post(API_V1_DATA_ENDPOINT)
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        //language=JSON
        .content("{\n"
            + "  \"name\" : \"" + caName + "\",\n"
            + "  \"type\" : \"certificate\",\n"
            + "  \"parameters\" : {\n"
            + "     \"is_ca\": true,\n"
            + "     \"common_name\": \"" + caCommonName + "\"\n"
            + "   },\n"
            + "\"additional_permissions\": [{"
            + "   \"actor\": \"" + UAA_OAUTH2_CLIENT_CREDENTIALS_ACTOR_ID + "\",\n"
            + "   \"operations\": [\"read\"]\n"
            + "}]}");

    this.mockMvc.perform(generateCAToRotateRequest)
        .andDo(print())
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
  }

  private void generateSignedCertificate(String certificateName, String certificatCN, String signingCA) throws Exception {
    MockHttpServletRequestBuilder generateCertSignedByOriginalCARequest = post(API_V1_DATA_ENDPOINT)
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        //language=JSON
        .content("{\n"
            + "  \"name\" : \"" + certificateName + "\",\n"
            + "  \"type\" : \"certificate\",\n"
            + "  \"parameters\" : {\n"
            + "\"ca\": \"" + signingCA + "\",\n"
            + "\"common_name\": \"" + certificatCN + "\"\n"
            + "},"
            + "\"overwrite\": true,"
            + "\"additional_permissions\": [{"
            + "\"actor\": \"" + UAA_OAUTH2_CLIENT_CREDENTIALS_ACTOR_ID + "\",\n"
            + "\"operations\": [\"write\"]\n"
            + "}]"
            + "}");

    String certGenerationResult = this.mockMvc.perform(generateCertSignedByOriginalCARequest)
        .andDo(print())
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
    assertThat((new JSONObject(certGenerationResult)).getString("value"), notNullValue());
  }

}
