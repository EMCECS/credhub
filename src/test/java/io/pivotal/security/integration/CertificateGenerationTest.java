package io.pivotal.security.integration;

import io.pivotal.security.CredentialManagerApp;
import io.pivotal.security.helper.RequestHelper;
import io.pivotal.security.util.DatabaseProfileResolver;
import org.bouncycastle.asn1.x509.AuthorityKeyIdentifier;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.SubjectKeyIdentifier;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
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

import java.io.ByteArrayInputStream;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.List;

import static io.pivotal.security.helper.RequestHelper.generateCa;
import static io.pivotal.security.util.AuthConstants.UAA_OAUTH2_CLIENT_CREDENTIALS_TOKEN;
import static io.pivotal.security.util.AuthConstants.UAA_OAUTH2_PASSWORD_GRANT_TOKEN;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@ActiveProfiles(value = "unit-test", resolver = DatabaseProfileResolver.class)
@SpringBootTest(classes = CredentialManagerApp.class)
@TestPropertySource(properties = "security.authorization.acls.enabled=true")
@Transactional
public class CertificateGenerationTest {
  @Autowired
  private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @Before
  public void beforeEach() throws Exception {
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();
  }

  private String createCaPost(String name) throws Exception{
    MockHttpServletRequestBuilder caPost = post("/api/v1/data")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        //language=JSON
        .content("{\n"
            + "  \"name\" : \"" + name + "\",\n"
            + "  \"type\" : \"certificate\",\n"
            + "  \"parameters\" : {\n"
            + "    \"common_name\" : \"federation\",\n"
            + "    \"is_ca\" : true,\n"
            + "    \"self_sign\" : true\n"
            + "  }\n"
            + "}");

    return this.mockMvc.perform(caPost)
        .andDo(print())
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
  }

  private String createCertificatePost(String name, String ca) throws Exception{
    MockHttpServletRequestBuilder certPost = post("/api/v1/data")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        //language=JSON
        .content("{\n"
            + "  \"name\" : \"" + name + "\",\n"
            + "  \"type\" : \"certificate\",\n"
            + "  \"parameters\" : {\n"
            + "    \"common_name\" : \"federation\",\n"
            + "    \"ca\" : \"" + ca + "\"\n"
            + "  }\n"
            + "}");

    return this.mockMvc.perform(certPost)
        .andDo(print())
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();
  }

  @Test
  public void certificateGeneration_shouldGenerateCorrectCertificate() throws Exception {
    String caResult = createCaPost("picard");

    String ca = (new JSONObject(caResult)).getJSONObject("value").getString("certificate");

    assertThat(ca, notNullValue());

    String certResult = createCertificatePost("riker", "picard");

    String certCa = (new JSONObject(certResult)).getJSONObject("value").getString("ca");
    String cert = (new JSONObject(certResult)).getJSONObject("value").getString("certificate");

    assertThat(certCa, equalTo(ca));

    CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
    X509Certificate caPem = (X509Certificate) certificateFactory
        .generateCertificate(new ByteArrayInputStream(ca.getBytes()));

    X509Certificate certPem = (X509Certificate) certificateFactory
        .generateCertificate(new ByteArrayInputStream(cert.getBytes()));

    byte[] subjectKeyIdDer = caPem.getExtensionValue(Extension.subjectKeyIdentifier.getId());
    SubjectKeyIdentifier subjectKeyIdentifier = SubjectKeyIdentifier.getInstance(JcaX509ExtensionUtils.parseExtensionValue(subjectKeyIdDer));
    byte[] subjectKeyId = subjectKeyIdentifier.getKeyIdentifier();

    byte[] authorityKeyIdDer = certPem.getExtensionValue(Extension.authorityKeyIdentifier.getId());
    AuthorityKeyIdentifier authorityKeyIdentifier = AuthorityKeyIdentifier.getInstance(JcaX509ExtensionUtils.parseExtensionValue(authorityKeyIdDer));
    byte[] authKeyId = authorityKeyIdentifier.getKeyIdentifier();

    assertThat(subjectKeyId, equalTo(authKeyId));
  }

  @Test
  public void findByCa_returnsTheNamesOfCertificatesSignedByThatCa() throws Exception{
    createCaPost("picard");
    createCertificatePost("riker", "picard");
    createCertificatePost("another_certificate", "picard");

    MockHttpServletRequestBuilder request = get("/api/v1/data?signed_by=" + "picard")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON);

    String caResult = this.mockMvc.perform(request).andDo(print())
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    final JSONArray credentials = (new JSONObject(caResult)).getJSONArray("credentials");
    final List<String> result = Arrays
        .asList(credentials.getString(0), credentials.getString(1));
    assertThat(result, containsInAnyOrder("/riker", "/another_certificate"));
  }


  @Test
  public void certificateGeneration_whenUserNotAuthorizedToReadCa_shouldReturnCorrectError() throws Exception {
    generateCa(mockMvc, "picard", UAA_OAUTH2_PASSWORD_GRANT_TOKEN);
    // try to generate with a different token that doesn't have read permission
    RequestHelper.expect404WhileGeneratingCertificate(mockMvc, "riker", UAA_OAUTH2_CLIENT_CREDENTIALS_TOKEN,
        "The request could not be completed because the credential does not exist or you do not have sufficient authorization.");
  }

  @Test
  public void invalidCertificateGenerationParameters_shouldResultInCorrectErrorMessage() throws Exception {
    MockHttpServletRequestBuilder request = post("/api/v1/data")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        //language=JSON
        .content("{\n"
            + "  \"name\" : \"picard\",\n"
            + "  \"type\" : \"certificate\",\n"
            + "  \"parameters\" : {\n"
            + "    \"common_name\" : \"65_abcdefghijklmnopqrstuvwxyzabcdefghijklmnopqrstuvwxyz0123456789\",\n"
            + "    \"self_sign\" : true\n"
            + "  }\n"
            + "}");
    String error = "The request could not be completed because the common name is too long. The max length for common name is 64 characters.";

    this.mockMvc
        .perform(request)
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error", equalTo(error)));
  }
}
