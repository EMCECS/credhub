package io.pivotal.security.integration;

import io.pivotal.security.CredentialManagerApp;
import io.pivotal.security.data.CredentialVersionDataService;
import io.pivotal.security.data.EncryptionKeyCanaryDataService;
import io.pivotal.security.domain.Encryptor;
import io.pivotal.security.domain.PasswordCredentialVersion;
import io.pivotal.security.domain.RsaCredentialVersion;
import io.pivotal.security.domain.SshCredentialVersion;
import io.pivotal.security.domain.UserCredentialVersion;
import io.pivotal.security.entity.EncryptionKeyCanary;
import io.pivotal.security.entity.PasswordCredentialVersionData;
import io.pivotal.security.helper.AuditingHelper;
import io.pivotal.security.repository.EventAuditRecordRepository;
import io.pivotal.security.repository.RequestAuditRecordRepository;
import io.pivotal.security.request.StringGenerationParameters;
import io.pivotal.security.util.CurrentTimeProvider;
import io.pivotal.security.util.DatabaseProfileResolver;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.function.Consumer;

import static io.pivotal.security.audit.AuditingOperationCode.CREDENTIAL_UPDATE;
import static io.pivotal.security.helper.RequestHelper.*;
import static io.pivotal.security.helper.TestHelper.mockOutCurrentTimeProvider;
import static io.pivotal.security.util.AuthConstants.UAA_OAUTH2_CLIENT_CREDENTIALS_TOKEN;
import static io.pivotal.security.util.AuthConstants.UAA_OAUTH2_PASSWORD_GRANT_ACTOR_ID;
import static io.pivotal.security.util.AuthConstants.UAA_OAUTH2_PASSWORD_GRANT_TOKEN;
import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@ActiveProfiles(value = "unit-test", resolver = DatabaseProfileResolver.class)
@SpringBootTest(classes = CredentialManagerApp.class)
@TestPropertySource(properties = "security.authorization.acls.enabled=true")
@Transactional
public class CredentialRegenerateTest {
  private static final Instant FROZEN_TIME = Instant.ofEpochSecond(1400011001L);

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private CredentialVersionDataService credentialVersionDataService;

  @Autowired
  private EncryptionKeyCanaryDataService canaryDataService;

  @Autowired
  private Encryptor encryptor;

  @MockBean
  private CurrentTimeProvider mockCurrentTimeProvider;

  @Autowired
  private RequestAuditRecordRepository requestAuditRecordRepository;

  @Autowired
  private EventAuditRecordRepository eventAuditRecordRepository;

  private AuditingHelper auditingHelper;
  private MockMvc mockMvc;
  private Consumer<Long> fakeTimeSetter;

  @Before
  public void beforeEach() {
    fakeTimeSetter = mockOutCurrentTimeProvider(mockCurrentTimeProvider);

    fakeTimeSetter.accept(FROZEN_TIME.toEpochMilli());
    mockMvc = MockMvcBuilders
        .webAppContextSetup(webApplicationContext)
        .apply(springSecurity())
        .build();

    auditingHelper = new AuditingHelper(requestAuditRecordRepository, eventAuditRecordRepository);
  }

  @Test
  public void regeneratingAPassword_regeneratesThePassword_andPersistsAnAuditEntry() throws Exception {
    PasswordCredentialVersion originalCredential = new PasswordCredentialVersion("/my-password");
    originalCredential.setEncryptor(encryptor);
    StringGenerationParameters generationParameters = new StringGenerationParameters();
    generationParameters.setExcludeNumber(true);
    originalCredential
        .setPasswordAndGenerationParameters("original-password", generationParameters);
    originalCredential.setVersionCreatedAt(FROZEN_TIME.plusSeconds(1));

    credentialVersionDataService.save(originalCredential);

    fakeTimeSetter.accept(FROZEN_TIME.plusSeconds(10).toEpochMilli());

    MockHttpServletRequestBuilder request = post("/api/v1/data")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{\"regenerate\":true,\"name\":\"my-password\"}");

    mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.type").value("password"))
        .andExpect(jsonPath("$.version_created_at").value(FROZEN_TIME.plusSeconds(10).toString()));

    final PasswordCredentialVersion newPassword = (PasswordCredentialVersion) credentialVersionDataService.findMostRecent("/my-password");

    assertThat(newPassword.getPassword(), not(equalTo("original-credential")));
    assertThat(newPassword.getGenerationParameters().isExcludeNumber(), equalTo(true));

    auditingHelper.verifyAuditing(CREDENTIAL_UPDATE, "/my-password", UAA_OAUTH2_PASSWORD_GRANT_ACTOR_ID, "/api/v1/data", 200);
  }

  @Test
  public void regeneratingAnRsaKey_regeneratesTheRsaKey_andPersistsAnAuditEntry() throws Exception {
    RsaCredentialVersion originalCredential = new RsaCredentialVersion("/my-rsa");
    originalCredential.setEncryptor(encryptor);
    originalCredential.setPrivateKey("original value");
    originalCredential.setVersionCreatedAt(FROZEN_TIME.plusSeconds(1));

    credentialVersionDataService.save(originalCredential);

    fakeTimeSetter.accept(FROZEN_TIME.plusSeconds(10).toEpochMilli());

    MockHttpServletRequestBuilder request = post("/api/v1/data")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{\"regenerate\":true,\"name\":\"my-rsa\"}");

    mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.type").value("rsa"))
        .andExpect(
            jsonPath("$.version_created_at").value(FROZEN_TIME.plusSeconds(10).toString()));

    RsaCredentialVersion newRsa = (RsaCredentialVersion) credentialVersionDataService.findMostRecent("/my-rsa");

    assertTrue(newRsa.getPublicKey().contains("-----BEGIN PUBLIC KEY-----"));
    assertTrue(newRsa.getPrivateKey().contains("-----BEGIN RSA PRIVATE KEY-----"));
    assertThat(originalCredential.getPublicKey(), not(equalTo(newRsa.getPublicKey())));
    assertThat(originalCredential.getPrivateKey(), not(equalTo(newRsa.getPrivateKey())));

    auditingHelper.verifyAuditing(CREDENTIAL_UPDATE, "/my-rsa", UAA_OAUTH2_PASSWORD_GRANT_ACTOR_ID, "/api/v1/data", 200);
  }

  @Test
  public void regeneratingAnSshKey_regeneratesTheSshKey_andPersistsAnAuditEntry() throws Exception {
    SshCredentialVersion originalCredential = new SshCredentialVersion("/my-ssh");
    originalCredential.setEncryptor(encryptor);
    originalCredential.setPrivateKey("original value");
    originalCredential.setVersionCreatedAt(FROZEN_TIME.plusSeconds(1));

    credentialVersionDataService.save(originalCredential);

    fakeTimeSetter.accept(FROZEN_TIME.plusSeconds(10).toEpochMilli());

    MockHttpServletRequestBuilder request = post("/api/v1/data")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{\"regenerate\":true,\"name\":\"my-ssh\"}");

    mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.type").value("ssh"))
        .andExpect(jsonPath("$.version_created_at").value(FROZEN_TIME.plusSeconds(10).toString()));

    final SshCredentialVersion newSsh = (SshCredentialVersion) credentialVersionDataService.findMostRecent("/my-ssh");

    assertThat(newSsh.getPrivateKey(), containsString("-----BEGIN RSA PRIVATE KEY-----"));
    assertThat(newSsh.getPublicKey(), containsString("ssh-rsa "));
    assertThat(newSsh.getPrivateKey(), not(equalTo(originalCredential.getPrivateKey())));
    assertThat(newSsh.getPublicKey(), not(equalTo(originalCredential.getPublicKey())));

    auditingHelper.verifyAuditing(CREDENTIAL_UPDATE, "/my-ssh", UAA_OAUTH2_PASSWORD_GRANT_ACTOR_ID, "/api/v1/data", 200);
  }

  @Test
  public void regeneratingAUser_regeneratesTheUser_andPersistsAnAuditEntry() throws Exception {
    UserCredentialVersion originalCredential = new UserCredentialVersion("/the-user");
    originalCredential.setEncryptor(encryptor);
    StringGenerationParameters generationParameters = new StringGenerationParameters();
    generationParameters.setExcludeNumber(true);
    generationParameters.setUsername("Darth Vader");
    originalCredential
        .setPassword("original-password");
    originalCredential.setUsername("Darth Vader");
    originalCredential.setSalt("pepper");
    originalCredential.setGenerationParameters(generationParameters);
    originalCredential.setVersionCreatedAt(FROZEN_TIME.plusSeconds(1));

    credentialVersionDataService.save(originalCredential);

    fakeTimeSetter.accept(FROZEN_TIME.plusSeconds(10).toEpochMilli());

    MockHttpServletRequestBuilder request = post("/api/v1/data")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{\"regenerate\":true,\"name\":\"the-user\"}");

    mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
        .andExpect(jsonPath("$.type").value("user"))
        .andExpect(jsonPath("$.version_created_at").value(FROZEN_TIME.plusSeconds(10).toString()));

    UserCredentialVersion newUser = (UserCredentialVersion) credentialVersionDataService.findMostRecent("/the-user");

    assertThat(newUser.getPassword(), not(equalTo(originalCredential.getPassword())));
    assertThat(newUser.getGenerationParameters().isExcludeNumber(), equalTo(true));
    assertThat(newUser.getUsername(), equalTo(originalCredential.getUsername()));

    auditingHelper.verifyAuditing(CREDENTIAL_UPDATE, "/the-user", UAA_OAUTH2_PASSWORD_GRANT_ACTOR_ID, "/api/v1/data", 200);
  }

  @Test
  public void regeneratingACertificateWithTransitionalTrue_regeneratesTheCertificate_andPersistsATransitionalEntry() throws Exception {
    generateCa(mockMvc, "test-cert", UAA_OAUTH2_PASSWORD_GRANT_TOKEN);

    MockHttpServletRequestBuilder request = post("/api/v1/regenerate")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{\"name\":\"test-cert\", \"transitional\":true}");

    mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version_transitional").value(true));
  }

  @Test
  public void regeneratingACertificateWithTransitionalFalse_regeneratesTheCertificate_andPersistsATransitionalEntry() throws Exception {
    generateCa(mockMvc, "test-cert", UAA_OAUTH2_PASSWORD_GRANT_TOKEN);

    MockHttpServletRequestBuilder request = post("/api/v1/regenerate")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{\"name\":\"test-cert\", \"transitional\":false}");

    mockMvc.perform(request)
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.version_transitional").value(false));
  }

  @Test
  public void regeneratingACredentialThatDoesNotExist_returnsAnError_andPersistsAnAuditEntry() throws Exception {
    MockHttpServletRequestBuilder request = post("/api/v1/data")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{\"regenerate\":true,\"name\":\"my-password\"}");

    String notFoundJson = "{" +
        "  \"error\": \"The request could not be completed because the credential does not exist or you do not have sufficient authorization.\"" +
        "}";

    mockMvc.perform(request)
        .andExpect(status().isNotFound())
        .andExpect(content().json(notFoundJson));

    auditingHelper.verifyAuditing(CREDENTIAL_UPDATE, "/my-password", UAA_OAUTH2_PASSWORD_GRANT_ACTOR_ID, "/api/v1/data", 404);
  }

  @Test
  public void regeneratingANonGeneratedPassword_returnsAnError_andPersistsAnAuditEntry() throws Exception {
    PasswordCredentialVersion originalCredential = new PasswordCredentialVersion("/my-password");
    originalCredential.setEncryptor(encryptor);
    originalCredential.setPasswordAndGenerationParameters("abcde", null);

    credentialVersionDataService.save(originalCredential);

    String cannotRegenerateJson = "{" +
        "  \"error\": \"The password could not be regenerated because the value was " +
        "statically set. Only generated passwords may be regenerated.\"" +
        "}";

    MockHttpServletRequestBuilder request = post("/api/v1/data")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{\"regenerate\":true,\"name\":\"my-password\"}");

    mockMvc.perform(request)
        .andExpect(content().json(cannotRegenerateJson));

    auditingHelper.verifyAuditing(CREDENTIAL_UPDATE, "/my-password", UAA_OAUTH2_PASSWORD_GRANT_ACTOR_ID, "/api/v1/data", 400);
  }

  @Test
  public void regeneratingANonGeneratedUser_returnsAnError_andPersistsAnAuditEntry() throws Exception {
    UserCredentialVersion originalCredential = new UserCredentialVersion("/my-user");
    originalCredential.setEncryptor(encryptor);
    originalCredential.setPassword("abcde");
    originalCredential.setUsername("username");
    originalCredential.setSalt("so salty");

    credentialVersionDataService.save(originalCredential);

    String cannotRegenerateJson = "{" +
        "  \"error\": \"The user could not be regenerated because the value was" +
        " statically set. Only generated users may be regenerated.\"" +
        "}";

    MockHttpServletRequestBuilder request = post("/api/v1/data")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{\"regenerate\":true,\"name\":\"my-user\"}");

    mockMvc.perform(request)
        .andExpect(content().json(cannotRegenerateJson));

    auditingHelper.verifyAuditing(CREDENTIAL_UPDATE, "/my-user", UAA_OAUTH2_PASSWORD_GRANT_ACTOR_ID, "/api/v1/data", 400);
  }

  @Test
  public void regeneratingAPasswordWithParametersThatCannotBeDecrypted_returnsAnError() throws Exception {
    EncryptionKeyCanary encryptionKeyCanary = new EncryptionKeyCanary();
    canaryDataService.save(encryptionKeyCanary);

    PasswordCredentialVersionData passwordCredentialData = new PasswordCredentialVersionData(
        "/my-password");
    PasswordCredentialVersion originalCredential = new PasswordCredentialVersion(passwordCredentialData);
    originalCredential.setEncryptor(encryptor);
    originalCredential
        .setPasswordAndGenerationParameters("abcde", new StringGenerationParameters());

    passwordCredentialData.getEncryptedValueData().setEncryptionKeyUuid(encryptionKeyCanary.getUuid());

    credentialVersionDataService.save(originalCredential);

    // language=JSON
    String cannotRegenerate = "{\n" +
        "  \"error\": \"The credential could not be accessed with the provided encryption keys. You must update your deployment configuration to continue" +
        ".\"\n" +
        "}";

    MockHttpServletRequestBuilder request = post("/api/v1/data")
        .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
        .accept(APPLICATION_JSON)
        .contentType(APPLICATION_JSON)
        .content("{\"regenerate\":true,\"name\":\"my-password\"}");

    mockMvc.perform(request)
        .andDo(print())
        .andExpect(status().isInternalServerError())
        .andExpect(content().json(cannotRegenerate));
  }

  @Test
  public void certificateRegeneration_whenUserNotAuthorizedToReadCa_shouldReturnCorrectError() throws Exception {
    generateCa(mockMvc, "picard", UAA_OAUTH2_PASSWORD_GRANT_TOKEN);

    grantPermissions(mockMvc, "/picard", UAA_OAUTH2_PASSWORD_GRANT_TOKEN, "uaa-client:credhub_test",
        "read");

    generateCertificate(mockMvc, "riker", "picard", UAA_OAUTH2_CLIENT_CREDENTIALS_TOKEN);

    revokePermissions(mockMvc, "/picard", UAA_OAUTH2_PASSWORD_GRANT_TOKEN, "uaa-client:credhub_test");

    expect404WhileRegeneratingCertificate(mockMvc, "riker", UAA_OAUTH2_CLIENT_CREDENTIALS_TOKEN,
        "The request could not be completed because the credential does not exist or you do not have sufficient authorization.");
  }
}
