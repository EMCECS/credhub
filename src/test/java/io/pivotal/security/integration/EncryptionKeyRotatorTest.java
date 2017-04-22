package io.pivotal.security.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greghaskins.spectrum.Spectrum;
import io.pivotal.security.CredentialManagerApp;
import io.pivotal.security.config.EncryptionKeyMetadata;
import io.pivotal.security.config.EncryptionKeysConfiguration;
import io.pivotal.security.data.EncryptionKeyCanaryDataService;
import io.pivotal.security.data.CredentialDataService;
import io.pivotal.security.domain.CertificateCredential;
import io.pivotal.security.domain.Credential;
import io.pivotal.security.domain.Encryptor;
import io.pivotal.security.domain.PasswordCredential;
import io.pivotal.security.entity.CredentialName;
import io.pivotal.security.entity.EncryptionKeyCanary;
import io.pivotal.security.entity.CertificateCredentialData;
import io.pivotal.security.entity.PasswordCredentialData;
import io.pivotal.security.repository.CredentialNameRepository;
import io.pivotal.security.repository.CredentialRepository;
import io.pivotal.security.request.StringGenerationParameters;
import io.pivotal.security.service.Encryption;
import io.pivotal.security.service.EncryptionKeyCanaryMapper;
import io.pivotal.security.service.EncryptionKeyRotator;
import io.pivotal.security.service.EncryptionService;
import io.pivotal.security.service.PasswordBasedKeyProxy;
import io.pivotal.security.util.DatabaseProfileResolver;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.data.domain.Slice;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.security.Key;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static com.google.common.collect.Lists.newArrayList;
import static com.greghaskins.spectrum.Spectrum.beforeEach;
import static com.greghaskins.spectrum.Spectrum.describe;
import static com.greghaskins.spectrum.Spectrum.it;
import static io.pivotal.security.helper.JsonHelper.parse;
import static io.pivotal.security.helper.SpectrumHelper.wireAndUnwire;
import static io.pivotal.security.service.EncryptionKeyCanaryMapper.CANARY_VALUE;
import static io.pivotal.security.service.PasswordBasedKeyProxy.generateSalt;
import static io.pivotal.security.util.AuthConstants.UAA_OAUTH2_PASSWORD_GRANT_TOKEN;
import static java.util.Collections.singletonList;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.hamcrest.core.IsNot.not;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ActiveProfiles(value = "unit-test", resolver = DatabaseProfileResolver.class)
@SpringBootTest(classes = CredentialManagerApp.class)
@RunWith(Spectrum.class)
public class EncryptionKeyRotatorTest {

  @Autowired
  private WebApplicationContext webApplicationContext;

  @Autowired
  private CredentialRepository credentialRepository;

  @SpyBean
  private CredentialDataService credentialDataService;

  @SpyBean
  private EncryptionKeyCanaryMapper encryptionKeyCanaryMapper;

  @Autowired
  private CredentialNameRepository credentialNameRepository;

  @Autowired
  private EncryptionKeyRotator encryptionKeyRotator;

  @Autowired
  private EncryptionKeyCanaryDataService encryptionKeyCanaryDataService;

  @Autowired
  private EncryptionService encryptionService;

  @Autowired
  private Encryptor encryptor;

  @SpyBean
  private EncryptionKeysConfiguration encryptionKeysConfiguration;

  private CertificateCredential credentialWithCurrentKey;
  private Credential credentialWithOldKey;
  private CertificateCredential credentialWithUnknownKey;
  private PasswordCredential password;
  private MockMvc mockMvc;
  private EncryptionKeyCanary unknownCanary;
  private EncryptionKeyCanary oldCanary;
  private String passwordName;

  {
    wireAndUnwire(this);

    describe("when data exists that is encrypted with an unknown key", () -> {
      beforeEach(() -> {
        credentialWithCurrentKey = new CertificateCredential("/current-key");
        credentialWithCurrentKey
            .setCa("my-ca")
            .setCertificate("my-cert")
            .setPrivateKey("cert-private-key");

        credentialDataService.save(credentialWithCurrentKey);

        final PasswordBasedKeyProxy keyProxy = new PasswordBasedKeyProxy("old-password",
            encryptionService);
        Key oldKey = keyProxy.deriveKey(generateSalt());

        oldCanary = new EncryptionKeyCanary();
        final Encryption canaryEncryption = encryptionService.encrypt(null, oldKey, CANARY_VALUE);
        oldCanary.setEncryptedCanaryValue(canaryEncryption.encryptedValue);
        oldCanary.setNonce(canaryEncryption.nonce);
        oldCanary = encryptionKeyCanaryDataService.save(oldCanary);

        when(encryptionKeyCanaryMapper.getKeyForUuid(oldCanary.getUuid())).thenReturn(oldKey);
        when(encryptionKeyCanaryMapper.getCanaryUuidsWithKnownAndInactiveKeys())
            .thenReturn(singletonList(oldCanary.getUuid()));

        final Encryption encryption = encryptionService
            .encrypt(oldCanary.getUuid(), oldKey, "old-certificate-private-key");
        CertificateCredentialData certificateCredentialData1 =
            new CertificateCredentialData("/old-key");
        certificateCredentialData1.setEncryptedValue(encryption.encryptedValue);
        certificateCredentialData1.setNonce(encryption.nonce);
        certificateCredentialData1.setEncryptionKeyUuid(oldCanary.getUuid());
        credentialWithOldKey = new CertificateCredential(certificateCredentialData1);
        credentialDataService.save(credentialWithOldKey);

        unknownCanary = new EncryptionKeyCanary();
        unknownCanary.setEncryptedCanaryValue("bad-encrypted-value".getBytes());
        unknownCanary.setNonce("bad-nonce".getBytes());
        unknownCanary = encryptionKeyCanaryDataService.save(unknownCanary);

        CertificateCredentialData certificateCredentialData2 = new CertificateCredentialData(
            "/unknown-key");
        credentialWithUnknownKey = new CertificateCredential(certificateCredentialData2);
        credentialWithUnknownKey
            .setPrivateKey("cert-private-key");
        certificateCredentialData2.setEncryptionKeyUuid(unknownCanary.getUuid());
        credentialDataService.save(credentialWithUnknownKey);

        passwordName = "/test-password";
        final Encryption credentialEncryption = encryptionService
            .encrypt(oldCanary.getUuid(), oldKey, "test-password-plaintext");
        PasswordCredentialData passwordCredentialData = new PasswordCredentialData(passwordName);
        passwordCredentialData.setEncryptedValue(credentialEncryption.encryptedValue);
        passwordCredentialData.setNonce(credentialEncryption.nonce);
        passwordCredentialData.setNonce(credentialEncryption.nonce);

        StringGenerationParameters parameters = new StringGenerationParameters();
        parameters.setExcludeNumber(true);
        final Encryption parameterEncryption = encryptionService
            .encrypt(oldCanary.getUuid(), oldKey,
                new ObjectMapper().writeValueAsString(parameters));
        passwordCredentialData.setEncryptedGenerationParameters(parameterEncryption.encryptedValue);
        passwordCredentialData.setParametersNonce(parameterEncryption.nonce);
        passwordCredentialData.setEncryptionKeyUuid(oldCanary.getUuid());

        password = new PasswordCredential(passwordCredentialData);

        credentialDataService.save(password);
      });

      it("should rotate data that it can decrypt (and it shouldn't loop forever!)", () -> {
        Slice<Credential> beforeRotation = credentialDataService
            .findEncryptedWithAvailableInactiveKey();
        int numberToRotate = beforeRotation.getNumberOfElements();

        assertThat(
            credentialRepository.findOneByUuid(credentialWithUnknownKey.getUuid())
                .getEncryptionKeyUuid(), equalTo(unknownCanary.getUuid()));

        encryptionKeyRotator.rotate();

        Slice<Credential> afterRotation = credentialDataService
            .findEncryptedWithAvailableInactiveKey();
        int numberToRotateWhenDone = afterRotation.getNumberOfElements();

        assertThat(numberToRotate, equalTo(2));
        assertThat(numberToRotateWhenDone, equalTo(0));

        List<UUID> uuids = beforeRotation.getContent().stream().map(Credential::getUuid)
            .collect(Collectors.toList());

        // Gets updated to use current key:
        assertThat(
            credentialRepository
                .findOneByUuid(credentialWithOldKey.getUuid())
                .getEncryptionKeyUuid(),
            equalTo(encryptionKeyCanaryMapper.getActiveUuid())
        );

        assertThat(uuids, hasItem(credentialWithOldKey.getUuid()));

        assertThat(credentialRepository.findOneByUuid(password.getUuid())
                .getEncryptionKeyUuid(), equalTo(encryptionKeyCanaryMapper.getActiveUuid()));
        assertThat(uuids, hasItem(password.getUuid()));

        // Unchanged because we don't have the key:
        assertThat(
            credentialRepository.findOneByUuid(credentialWithUnknownKey.getUuid())
                .getEncryptionKeyUuid(), equalTo(unknownCanary.getUuid()));
        assertThat(uuids, not(hasItem(credentialWithUnknownKey.getUuid())));

        // Unchanged because it's already up to date:
        assertThat(
            credentialRepository.findOneByUuid(credentialWithCurrentKey.getUuid())
                .getEncryptionKeyUuid(), equalTo(encryptionKeyCanaryMapper.getActiveUuid()));
        assertThat(uuids, not(hasItem(credentialWithCurrentKey.getUuid())));

        PasswordCredential rotatedPassword = (PasswordCredential) credentialDataService
            .findMostRecent(passwordName);
        assertThat(rotatedPassword.getPassword(), equalTo("test-password-plaintext"));
        assertThat(rotatedPassword.getGenerationParameters(), samePropertyValuesAs(
            new StringGenerationParameters()
                .setExcludeNumber(true)
                .setLength(23))
        );
      });
    });

    describe("rotation", () -> {
      beforeEach(() -> {
        mockMvc = MockMvcBuilders
            .webAppContextSetup(webApplicationContext)
            .apply(springSecurity())
            .build();
      });

      it("can rotate password credentials", () -> {
        MockHttpServletRequestBuilder post = post("/api/v1/data")
            .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
            .accept(APPLICATION_JSON)
            .contentType(APPLICATION_JSON)
            .content("{"
                + "  \"name\": \"/cred1\","
                + "  \"type\": \"password\""
                + "}");

        String content = this.mockMvc.perform(post).andExpect(status().isOk()).andReturn()
            .getResponse().getContentAsString();
        String originalPassword = parse(content).get("value").textValue();

        CredentialName credentialName = credentialNameRepository
            .findOneByNameIgnoreCase("/cred1");

        final PasswordCredentialData firstEncryption =
            (PasswordCredentialData) credentialRepository.findAllByCredentialNameUuid(credentialName.getUuid()).get(0);

        setActiveKey(1);

        encryptionKeyRotator.rotate();

        final PasswordCredentialData secondEncryption =
            (PasswordCredentialData) credentialRepository.findAllByCredentialNameUuid(credentialName.getUuid()).get(0);
        assertThat(firstEncryption.getEncryptedValue(),
            not(equalTo(secondEncryption.getEncryptedValue())));
        assertThat(firstEncryption.getEncryptedGenerationParameters(),
            not(equalTo(secondEncryption.getEncryptedGenerationParameters())));

        final MockHttpServletRequestBuilder get = get("/api/v1/data?name=cred1")
            .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN);
        this.mockMvc.perform(get).andExpect(status().isOk())
            .andExpect(jsonPath(".data[0].value").value(originalPassword));
      });

      it("can rotate certificate credentials", () -> {
        MockHttpServletRequestBuilder post = post("/api/v1/data")
            .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN)
            .accept(APPLICATION_JSON)
            .contentType(APPLICATION_JSON)
            .content("{"
                + "  \"name\": \"/cred1\","
                + "  \"type\": \"certificate\","
                + "  \"parameters\": { "
                + "    \"is_ca\": true,\n"
                + "    \"common_name\": \"Pivotal CA\""
                + "  }"
                + "}");

        String content = this.mockMvc.perform(post).andExpect(status().isOk()).andReturn()
            .getResponse().getContentAsString();
        String originalCert = parse(content).get("value").get("private_key").textValue();

        CredentialName credentialName = credentialNameRepository
            .findOneByNameIgnoreCase("/cred1");

        final CertificateCredentialData firstEncryption =
            (CertificateCredentialData) credentialRepository.findAllByCredentialNameUuid(credentialName.getUuid()).get(0);

        setActiveKey(1);

        encryptionKeyRotator.rotate();

        final CertificateCredentialData secondEncryption =
            (CertificateCredentialData) credentialRepository.findAllByCredentialNameUuid(credentialName.getUuid()).get(0);
        assertThat(firstEncryption.getEncryptedValue(),
            not(equalTo(secondEncryption.getEncryptedValue())));

        final MockHttpServletRequestBuilder get = get("/api/v1/data?name=cred1")
            .header("Authorization", "Bearer " + UAA_OAUTH2_PASSWORD_GRANT_TOKEN);
        this.mockMvc.perform(get).andExpect(status().isOk())
            .andExpect(jsonPath("$.data[0].value.private_key").value(originalCert));
      });
    });
  }

  private void setActiveKey(int index) {
    List<EncryptionKeyMetadata> keys = new ArrayList<>();
    for (EncryptionKeyMetadata encryptionKeyMetadata : encryptionKeysConfiguration.getKeys()) {
      keys.add(new EncryptionKeyMetadata(encryptionKeyMetadata.getDevKey(),
          encryptionKeyMetadata.getEncryptionKeyName(), false,
          encryptionKeyMetadata.getEncryptionPassword()));
    }

    keys.get(index).setActive(true);

    doReturn(
        keys
    ).when(encryptionKeysConfiguration).getKeys();

    encryptionKeyCanaryMapper.mapUuidsToKeys();
  }
}


