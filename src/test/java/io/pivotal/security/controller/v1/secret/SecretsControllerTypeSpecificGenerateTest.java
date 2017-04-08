package io.pivotal.security.controller.v1.secret;

import static com.greghaskins.spectrum.Spectrum.beforeEach;
import static com.greghaskins.spectrum.Spectrum.describe;
import static com.greghaskins.spectrum.Spectrum.it;
import static io.pivotal.security.entity.AuditingOperationCode.CREDENTIAL_ACCESS;
import static io.pivotal.security.entity.AuditingOperationCode.CREDENTIAL_UPDATE;
import static io.pivotal.security.helper.SpectrumHelper.mockOutCurrentTimeProvider;
import static io.pivotal.security.helper.SpectrumHelper.wireAndUnwire;
import static io.pivotal.security.request.AccessControlOperation.DELETE;
import static io.pivotal.security.request.AccessControlOperation.READ;
import static io.pivotal.security.request.AccessControlOperation.READ_ACL;
import static io.pivotal.security.request.AccessControlOperation.WRITE;
import static io.pivotal.security.request.AccessControlOperation.WRITE_ACL;
import static io.pivotal.security.util.AuditLogTestHelper.resetAuditLogMock;
import static io.pivotal.security.util.AuthConstants.UAA_OAUTH2_PASSWORD_GRANT_TOKEN;
import static io.pivotal.security.util.MultiJsonPathMatcher.multiJsonPath;
import static java.util.Arrays.asList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.beans.SamePropertyValuesAs.samePropertyValuesAs;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greghaskins.spectrum.Spectrum;
import com.greghaskins.spectrum.Spectrum.Block;
import io.pivotal.security.CredentialManagerApp;
import io.pivotal.security.data.SecretDataService;
import io.pivotal.security.domain.Encryptor;
import io.pivotal.security.domain.NamedPasswordSecret;
import io.pivotal.security.domain.NamedRsaSecret;
import io.pivotal.security.domain.NamedSecret;
import io.pivotal.security.domain.NamedSshSecret;
import io.pivotal.security.exceptions.ParameterizedValidationException;
import io.pivotal.security.generator.PassayStringSecretGenerator;
import io.pivotal.security.generator.RsaGenerator;
import io.pivotal.security.generator.SshGenerator;
import io.pivotal.security.helper.JsonHelper;
import io.pivotal.security.request.AccessControlEntry;
import io.pivotal.security.request.BaseSecretGenerateRequest;
import io.pivotal.security.request.DefaultSecretGenerateRequest;
import io.pivotal.security.request.PasswordGenerationParameters;
import io.pivotal.security.request.RsaGenerationParameters;
import io.pivotal.security.request.SshGenerationParameters;
import io.pivotal.security.secret.Password;
import io.pivotal.security.secret.RsaKey;
import io.pivotal.security.secret.SshKey;
import io.pivotal.security.service.AuditLogService;
import io.pivotal.security.service.AuditRecordBuilder;
import io.pivotal.security.service.EncryptionKeyCanaryMapper;
import io.pivotal.security.service.GenerateService;
import io.pivotal.security.util.CurrentTimeProvider;
import io.pivotal.security.util.DatabaseProfileResolver;
import io.pivotal.security.util.ExceptionThrowingFunction;
import io.pivotal.security.view.AccessControlListResponse;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@RunWith(Spectrum.class)
@ActiveProfiles(value = "unit-test", resolver = DatabaseProfileResolver.class)
@SpringBootTest(classes = CredentialManagerApp.class)
@SuppressWarnings("Duplicates")
public class SecretsControllerTypeSpecificGenerateTest {

  @Autowired
  WebApplicationContext webApplicationContext;

  @SpyBean
  SecretDataService secretDataService;

  @SpyBean
  GenerateService generateService;

  @MockBean
  CurrentTimeProvider mockCurrentTimeProvider;

  @MockBean
  PassayStringSecretGenerator secretGenerator;

  @MockBean
  SshGenerator sshGenerator;

  @MockBean
  RsaGenerator rsaGenerator;

  @SpyBean
  AuditLogService auditLogService;

  @SpyBean
  ObjectMapper objectMapper;

  @Autowired
  private Encryptor encryptor;

  @Autowired
  EncryptionKeyCanaryMapper encryptionKeyCanaryMapper;

  private MockMvc mockMvc;
  private Instant frozenTime = Instant.ofEpochSecond(1400011001L);
  private Consumer<Long> fakeTimeSetter;
  private AuditRecordBuilder auditRecordBuilder;
  private UUID uuid;

  private final String fakePassword = "generated-secret";
  private final String publicKey = "public_key";
  private final String privateKey = "private_key";
  private final String secretName = "/my-namespace/subTree/secret-name";
  private ResultActions response;
  private MockHttpServletRequestBuilder post;

  {
    wireAndUnwire(this);

    beforeEach(() -> {
      fakeTimeSetter = mockOutCurrentTimeProvider(mockCurrentTimeProvider);

      fakeTimeSetter.accept(frozenTime.toEpochMilli());
      mockMvc = MockMvcBuilders
          .webAppContextSetup(webApplicationContext)
          .apply(springSecurity())
          .build();
      when(secretGenerator.generateSecret(any(PasswordGenerationParameters.class)))
          .thenReturn(new Password(fakePassword));

      when(sshGenerator.generateSecret(any(SshGenerationParameters.class)))
          .thenReturn(new SshKey(publicKey, privateKey, null));

      when(rsaGenerator.generateSecret(any(RsaGenerationParameters.class)))
          .thenReturn(new RsaKey(publicKey, privateKey));

      auditRecordBuilder = new AuditRecordBuilder();
      resetAuditLogMock(auditLogService, auditRecordBuilder);
    });

    describe("password", this.<NamedPasswordSecret>testSecretBehavior(
        new Object[] { "$.value", fakePassword },
        "password",
        "{\"exclude_number\": true}",
        (passwordSecret) -> {
          assertThat(passwordSecret.getGenerationParameters().isExcludeNumber(), equalTo(true));
          assertThat(passwordSecret.getPassword(), equalTo(fakePassword));
        },
        () -> new NamedPasswordSecret(secretName)
            .setEncryptor(encryptor)
            .setEncryptionKeyUuid(encryptionKeyCanaryMapper.getActiveUuid())
            .setPasswordAndGenerationParameters(fakePassword, new PasswordGenerationParameters().setExcludeNumber(true))
            .setUuid(uuid)
            .setVersionCreatedAt(frozenTime.minusSeconds(1))
    ));

    describe("ssh", this.<NamedSshSecret>testSecretBehavior(
        new Object[] {
            "$.value.public_key", "public_key",
            "$.value.private_key", "private_key",
            "$.value.public_key_fingerprint", null},
        "ssh",
        "null",
        (sshSecret) -> {
          assertThat(sshSecret.getPublicKey(), equalTo(publicKey));
          assertThat(sshSecret.getPrivateKey(), equalTo(privateKey));
        },
        () -> new NamedSshSecret(secretName)
            .setEncryptor(encryptor)
            .setEncryptionKeyUuid(encryptionKeyCanaryMapper.getActiveUuid())
            .setPrivateKey(privateKey)
            .setPublicKey(publicKey)
            .setUuid(uuid)
            .setVersionCreatedAt(frozenTime.minusSeconds(1)))
    );

    describe("rsa", this.<NamedRsaSecret>testSecretBehavior(
        new Object[] {
            "$.value.public_key", "public_key",
            "$.value.private_key", "private_key"},
        "rsa",
        "null",
        (rsaSecret) -> {
          assertThat(rsaSecret.getPublicKey(), equalTo(publicKey));
          assertThat(rsaSecret.getPrivateKey(), equalTo(privateKey));
        },
        () -> new NamedRsaSecret(secretName)
            .setEncryptor(encryptor)
            .setEncryptionKeyUuid(encryptionKeyCanaryMapper.getActiveUuid())
            .setPrivateKey(privateKey)
            .setPublicKey(publicKey)
            .setUuid(uuid)
            .setVersionCreatedAt(frozenTime.minusSeconds(1)))
    );
  }

  private <T extends NamedSecret> Block testSecretBehavior(
      Object[] typeSpecificResponseFields,
      String secretType,
      String generationParameters,
      Consumer<T> namedSecretAssertions,
      Supplier<T> existingSecretProvider) {
    return () -> {
      describe("for a new secret", () -> {
        beforeEach(() -> {
          post = post("/api/v1/data")
              .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
              .accept(APPLICATION_JSON)
              .contentType(APPLICATION_JSON)
              .content("{" +
                  "\"name\":\"" + secretName + "\"," +
                  "\"type\":\"" + secretType + "\"," +
                  "\"parameters\":" + generationParameters + "," +
                  "\"overwrite\":" + false +
                  "}");
        });

        describe("with perform in beforeEach", () -> {
          beforeEach(() -> {
            response = mockMvc.perform(post).andDo(print());
          });

          it("should return the expected response", () -> {
            ArgumentCaptor<NamedSecret> argumentCaptor = ArgumentCaptor.forClass(NamedSecret.class);
            verify(secretDataService, times(1)).save(argumentCaptor.capture());
            response.andExpect(multiJsonPath(typeSpecificResponseFields))
                .andExpect(multiJsonPath(
                    "$.type", secretType,
                    "$.id", argumentCaptor.getValue().getUuid().toString(),
                    "$.version_created_at", frozenTime.toString()))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON));
          });

          it("asks the data service to persist the secret", () -> {
            verify(generateService, times(1))
                .performGenerate(
                    isA(AuditRecordBuilder.class),
                    isA(BaseSecretGenerateRequest.class),
                    isA(AccessControlEntry.class));
            ArgumentCaptor<NamedSecret> argumentCaptor = ArgumentCaptor.forClass(NamedSecret.class);
            verify(secretDataService, times(1)).save(argumentCaptor.capture());

            T newSecret = (T) argumentCaptor.getValue();

            namedSecretAssertions.accept(newSecret);
          });

          it("persists an audit entry", () -> {
            verify(auditLogService).performWithAuditing(isA(ExceptionThrowingFunction.class));
            assertThat(auditRecordBuilder.getOperationCode(), equalTo(CREDENTIAL_UPDATE));
          });

          it("should create an ACL with the current user having read and write permissions", () -> {
            response.andExpect(status().isOk());
            MvcResult result = mockMvc.perform(get("/api/v1/acls?credential_name=" + secretName)
                .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN))
                .andDo(print())
                .andExpect(status().isOk())
                .andReturn();
            String content = result.getResponse().getContentAsString();
            AccessControlListResponse acl = JsonHelper
                .deserialize(content, AccessControlListResponse.class);

            assertThat(acl.getCredentialName(), equalTo(secretName));
            assertThat(acl.getAccessControlList(), containsInAnyOrder(
                samePropertyValuesAs(
                    new AccessControlEntry("uaa-user:df0c1a26-2875-4bf5-baf9-716c6bb5ea6d",
                        asList(READ, WRITE, DELETE, READ_ACL, WRITE_ACL)))));
          });
        });

        it("validates the request body", () -> {
          DefaultSecretGenerateRequest request = mock(DefaultSecretGenerateRequest.class);
          doThrow(new ParameterizedValidationException("error.request_validation_test")).when(request).validate();
          doReturn(request).when(objectMapper).readValue(anyString(), any(Class.class));
          response = mockMvc.perform(post)
              .andDo(print())
              .andExpect(status().isBadRequest())
              .andExpect(content().json("{\"error\":\"Request body was validated and ControllerAdvice worked.\"}"));
        });
      });

      describe("with an existing secret", () -> {
        beforeEach(() -> {
          uuid = UUID.randomUUID();
          doReturn(existingSecretProvider.get()).when(secretDataService).findMostRecent(secretName);
          resetAuditLogMock(auditLogService, auditRecordBuilder);
        });

        describe("with the overwrite flag set to true", () -> {
          beforeEach(() -> {
            final MockHttpServletRequestBuilder post = post("/api/v1/data")
                .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
                .accept(APPLICATION_JSON)
                .contentType(APPLICATION_JSON)
                .content("{" +
                    "  \"type\":\"" + secretType + "\"," +
                    "  \"name\":\"" + secretName + "\"," +
                    "  \"overwrite\":true" +
                    "}");

            response = mockMvc.perform(post);
          });

          it("should return the correct response", () -> {
            ArgumentCaptor<NamedSecret> argumentCaptor = ArgumentCaptor.forClass(NamedSecret.class);
            verify(secretDataService, times(1)).save(argumentCaptor.capture());

            response.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(multiJsonPath(typeSpecificResponseFields))
                .andExpect(multiJsonPath(
                    "$.type", secretType,
                    "$.id", argumentCaptor.getValue().getUuid().toString(),
                    "$.version_created_at", frozenTime.toString()));
          });

          it("asks the data service to persist the secret", () -> {
            T namedSecret = (T) secretDataService.findMostRecent(secretName);
            namedSecretAssertions.accept(namedSecret);
          });

          it("persists an audit entry", () -> {
            verify(auditLogService).performWithAuditing(isA(ExceptionThrowingFunction.class));
            assertThat(auditRecordBuilder.getOperationCode(), equalTo(CREDENTIAL_UPDATE));
          });
        });

        describe("with the overwrite flag set to false", () -> {
          beforeEach(() -> {
            final MockHttpServletRequestBuilder post = post("/api/v1/data")
                .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
                .accept(APPLICATION_JSON)
                .contentType(APPLICATION_JSON)
                .content("{\"type\":\"" + secretType + "\",\"name\":\"" + secretName + "\"}");

            response = mockMvc.perform(post);
          });

          it("should return the existing values", () -> {
            response.andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(APPLICATION_JSON))
                .andExpect(multiJsonPath(typeSpecificResponseFields))
                .andExpect(multiJsonPath(
                    "$.id", uuid.toString(),
                    "$.version_created_at", frozenTime.minusSeconds(1).toString()));
          });

          it("should not persist the secret", () -> {
            verify(secretDataService, times(0)).save(any(NamedSecret.class));
          });

          it("persists an audit entry", () -> {
            verify(auditLogService).performWithAuditing(isA(ExceptionThrowingFunction.class));
            assertThat(auditRecordBuilder.getOperationCode(), equalTo(CREDENTIAL_ACCESS));
          });
        });
      });
    };
  }
}
