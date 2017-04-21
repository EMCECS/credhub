package io.pivotal.security.integration;

import io.pivotal.security.CredentialManagerApp;
import io.pivotal.security.service.EncryptionKeyCanaryMapper;
import io.pivotal.security.util.DatabaseProfileResolver;
import org.bouncycastle.asn1.x509.Extension;
import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.ByteArrayInputStream;
import java.io.UnsupportedEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;

import static io.pivotal.security.util.AuthConstants.UAA_OAUTH2_PASSWORD_GRANT_TOKEN;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringJUnit4ClassRunner.class)
@ActiveProfiles(value = "unit-test", resolver = DatabaseProfileResolver.class)
@SpringBootTest(classes = CredentialManagerApp.class)
public class GenerationTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  private MockMvc mockMvc;

  @Before
  public void beforeEach() throws Exception {
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();

    cleanUpDatabase(webApplicationContext);
  }


  @Test
  public void userGeneration_shouldGenerateCorrectUsernameAndPassword() throws Exception {
    getPost("/cred1");
    getPost("/cred2");

    MvcResult cred1 = this.mockMvc.perform(get("/api/v1/data?name=/cred1")
      .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
      .accept(APPLICATION_JSON)
      .contentType(APPLICATION_JSON))
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].value.username", isUsername()))
      .andExpect(jsonPath("$.data[0].value.password", isPassword()))
      .andReturn();


    MvcResult cred2 = this.mockMvc.perform(get("/api/v1/data?name=/cred2")
      .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
      .accept(APPLICATION_JSON)
      .contentType(APPLICATION_JSON))
      .andDo(print())
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].value.username", isUsername()))
      .andExpect(jsonPath("$.data[0].value.password", isPassword()))
      .andReturn();

    JSONObject jsonCred1 = getJsonObject(cred1);
    JSONObject jsonCred2 = getJsonObject(cred2);

    assertThat(jsonCred1.getString("username"), not(equalTo(jsonCred2.getString("username"))));
    assertThat(jsonCred1.getString("password"), not(equalTo(jsonCred2.getString("password"))));
  }

  @Test
  public void userGeneration_shouldGenerateOnlyPasswordWhenGivenStaticUsername() throws Exception{
    MockHttpServletRequestBuilder post = post("/api/v1/data")
      .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
      .accept(APPLICATION_JSON)
      .contentType(APPLICATION_JSON)
      //language=JSON
      .content("{  \"name\": \"cred1\", \n" +
        "  \"type\": \"user\", \n" +
        "  \"value\": {\n" +
        "    \"username\": \"luke\" \n" +
        "  }\n" +
        "}");

    this.mockMvc.perform(post)
      .andDo(print())
      .andExpect(status().isOk());

    this.mockMvc.perform(get("/api/v1/data?name=/cred1")
      .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
      .accept(APPLICATION_JSON)
      .contentType(APPLICATION_JSON))
      .andDo(print())
      .andExpect(status().isOk())
      .andExpect(jsonPath("$.data[0].value.username", equalTo("luke")))
      .andExpect(jsonPath("$.data[0].value.password", isPassword()))
      .andReturn();
  }

  @Test
  public void certificateGeneration_shouldGenerateCorrectCertificate() throws Exception {
    MockHttpServletRequestBuilder caPost = post("/api/v1/data")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        //language=JSON
        .content("{\n"
            + "  \"name\" : \"picard\",\n"
            + "  \"type\" : \"certificate\",\n"
            + "  \"parameters\" : {\n"
            + "    \"common_name\" : \"federation\",\n"
            + "    \"is_ca\" : true,\n"
            + "    \"self_sign\" : true\n"
            + "  }\n"
            + "}");

     String caResult = this.mockMvc.perform(caPost)
        .andDo(print())
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    String ca = (new JSONObject(caResult)).getJSONObject("value").getString("certificate");

   assertThat(ca, notNullValue());

    MockHttpServletRequestBuilder certPost = post("/api/v1/data")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        //language=JSON
        .content("{\n"
            + "  \"name\" : \"riker\",\n"
            + "  \"type\" : \"certificate\",\n"
            + "  \"parameters\" : {\n"
            + "    \"common_name\" : \"federation\",\n"
            + "    \"ca\" : \"picard\"\n"
            + "  }\n"
            + "}");

    String certResult = this.mockMvc.perform(certPost)
        .andDo(print())
        .andExpect(status().isOk())
        .andReturn().getResponse().getContentAsString();

    String certCa = (new JSONObject(certResult)).getJSONObject("value").getString("ca");
    String cert = (new JSONObject(certResult)).getJSONObject("value").getString("certificate");

    assertThat(certCa, equalTo(ca));

    CertificateFactory certificateFactory = CertificateFactory.getInstance("X.509");
    X509Certificate caPem = (X509Certificate) certificateFactory
        .generateCertificate(new ByteArrayInputStream(ca.getBytes()));

    X509Certificate certPem = (X509Certificate) certificateFactory
        .generateCertificate(new ByteArrayInputStream(cert.getBytes()));

    assertThat(caPem.getExtensionValue(Extension.subjectKeyIdentifier.getId()), equalTo(caPem.getExtensionValue(Extension.authorityKeyIdentifier.getId())));
    assertThat(caPem.getExtensionValue(Extension.subjectKeyIdentifier.getId()), equalTo(certPem.getExtensionValue(Extension.authorityKeyIdentifier.getId())));
  }

  private JSONObject getJsonObject(MvcResult cred1) throws UnsupportedEncodingException {
    JSONObject jsonCred1 = new JSONObject(cred1.getResponse().getContentAsString());
    return jsonCred1
        .getJSONArray("data")
        .getJSONObject(0)
        .getJSONObject("value");
  }

  private static void cleanUpDatabase(ApplicationContext applicationContext) {
    JdbcTemplate jdbcTemplate = applicationContext.getBean(JdbcTemplate.class);
    jdbcTemplate.execute("delete from credential_name");
    jdbcTemplate.execute("truncate table auth_failure_audit_record");
    jdbcTemplate.execute("delete from event_audit_record");
    jdbcTemplate.execute("delete from request_audit_record");
    jdbcTemplate.execute("delete from encryption_key_canary");
    jdbcTemplate.execute("truncate table access_entry");

    EncryptionKeyCanaryMapper encryptionKeyCanaryMapper = applicationContext
        .getBean(EncryptionKeyCanaryMapper.class);
    encryptionKeyCanaryMapper.mapUuidsToKeys();
  }


  private void getPost(String name) throws Exception {
    MockHttpServletRequestBuilder post = post("/api/v1/data")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{"
            + "  \"name\": \"" + name + "\","
            + "  \"type\": \"user\""
            + "}");

    this.mockMvc.perform(post)
        .andExpect(status().isOk());
  }

  private Matcher<String> isUsername() {
    return new BaseMatcher<String>() {
      @Override
      public boolean matches(final Object item) {
        final String username = (String) item;
        boolean matches = username.length() == 20;
        matches = matches && username.matches("[a-zA-Z]+");
        return matches;
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText("a 20 character string with only alpha characters");
      }
    };
  }

  private Matcher<String> isPassword() {
    return new BaseMatcher<String>() {
      @Override
      public boolean matches(final Object item) {
        final String username = (String) item;
        boolean matches = username.length() == 30;
        matches = matches && username.matches("[a-zA-Z0-9]+");
        return matches;
      }

      @Override
      public void describeTo(final Description description) {
        description.appendText("a 30 character string with only alpha numeric characters");
      }
    };
  }
}
