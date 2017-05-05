package io.pivotal.security.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import io.pivotal.security.credential.CertificateCredentialValue;
import io.pivotal.security.credential.JsonCredentialValue;
import io.pivotal.security.credential.RsaCredentialValue;
import io.pivotal.security.credential.SshCredentialValue;
import io.pivotal.security.credential.StringCredentialValue;
import io.pivotal.security.credential.UserCredentialValue;
import io.pivotal.security.request.StringGenerationParameters;
import io.pivotal.security.service.Encryption;
import net.minidev.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.Arrays;
import java.util.UUID;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.beans.SamePropertyValuesAs.samePropertyValuesAs;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class CredentialFactoryTest {

  private static final String CREDENTIAL_NAME = "/test";
  private static final String PLAINTEXT_VALUE = "test-value";
  private final ImmutableMap<String, Object> jsonValueMap = ImmutableMap.<String, Object>builder()
      .put("key", "value")
      .put("array", Arrays.asList("foo", "bar"))
      .build();
  private final String jsonValueJsonString = JSONObject.toJSONString(jsonValueMap);
  private CredentialFactory subject;
  private ObjectMapper objectMapper;
  private StringGenerationParameters generationParameters;

  @Before
  public void setup() throws JsonProcessingException {
    Encryptor encryptor = mock(Encryptor.class);
    subject = new CredentialFactory(encryptor);
    objectMapper = new ObjectMapper();
    generationParameters = new StringGenerationParameters().setExcludeNumber(true).setLength(PLAINTEXT_VALUE.length());

    UUID encryptionKeyUuid = UUID.randomUUID();
    Encryption encryption = new Encryption(encryptionKeyUuid, PLAINTEXT_VALUE.getBytes(), "test-nonce".getBytes());
    when(encryptor.encrypt(PLAINTEXT_VALUE)).thenReturn(encryption);
    when(encryptor.decrypt(encryption)).thenReturn(PLAINTEXT_VALUE);

    String generationParametersJsonString = objectMapper.writeValueAsString(generationParameters);
    Encryption parametersEncryption = new Encryption(encryptionKeyUuid, "test-parameters".getBytes(), "test-parameters-nonce".getBytes());
    when(encryptor.encrypt(generationParametersJsonString)).thenReturn(parametersEncryption);
    when(encryptor.decrypt(parametersEncryption)).thenReturn(generationParametersJsonString);

    Encryption jsonEncryption =  new Encryption(encryptionKeyUuid, jsonValueJsonString.getBytes(), "test-nonce".getBytes());
    when(encryptor.encrypt(jsonValueJsonString)).thenReturn(jsonEncryption);
    when(encryptor.decrypt(jsonEncryption)).thenReturn(jsonValueJsonString);
  }

  @Test
  public void makeCredentialFromRequest_givenAnExistingPassword_copiesCredentialNameReference() throws Exception {
    StringCredentialValue passwordValue = new StringCredentialValue(PLAINTEXT_VALUE);

    Credential existingCredential = new PasswordCredential(CREDENTIAL_NAME);
    Credential credential =
        subject.makeNewCredentialVersion(
            "password",
            CREDENTIAL_NAME,
            passwordValue,
            existingCredential,
            generationParameters);
    assertThat(credential.getCredentialName(), equalTo(existingCredential.getCredentialName()));
  }

  @Test
  public void makeCredentialFromRequest_givenPasswordType_andNoExisting_returnsPasswordCredential() throws Exception {
    StringCredentialValue passwordValue = new StringCredentialValue(PLAINTEXT_VALUE);

    PasswordCredential credential =
        (PasswordCredential) subject.makeNewCredentialVersion(
            "password",
            CREDENTIAL_NAME,
            passwordValue,
            null,
            generationParameters);
    assertThat(credential.getCredentialName().getName(), equalTo(CREDENTIAL_NAME));
    assertThat(credential.getPassword(), equalTo(PLAINTEXT_VALUE));
    assertThat(credential.getGenerationParameters(), samePropertyValuesAs(generationParameters));
  }

  @Test
  public void makeCredentialFromRequest_givenValueType_andNoExisting_returnsValueCredential() throws Exception {
    StringCredentialValue passwordValue = new StringCredentialValue(PLAINTEXT_VALUE);

    ValueCredential credential =
        (ValueCredential) subject.makeNewCredentialVersion(
            "value",
            CREDENTIAL_NAME,
            passwordValue,
            null,
            null);
    assertThat(credential.getCredentialName().getName(), equalTo(CREDENTIAL_NAME));
    assertThat(credential.getValue(), equalTo(PLAINTEXT_VALUE));
  }

  @Test
  public void makeCredentialFromRequest_givenCertificateType_andNoExisting_returnsCertificateCredential() throws Exception {
    CertificateCredentialValue certificateValue = new CertificateCredentialValue(
        "ca-certificate",
        "certificate",
        PLAINTEXT_VALUE,
        "my-ca");

    CertificateCredential credential =
        (CertificateCredential) subject.makeNewCredentialVersion(
            "certificate",
            CREDENTIAL_NAME,
            certificateValue,
            null,
            null);
    assertThat(credential.getCredentialName().getName(), equalTo(CREDENTIAL_NAME));
    assertThat(credential.getCa(), equalTo("ca-certificate"));
    assertThat(credential.getCertificate(), equalTo("certificate"));
    assertThat(credential.getPrivateKey(), equalTo(PLAINTEXT_VALUE));
    assertThat(credential.getCaName(), equalTo("/my-ca"));
  }

  @Test
  public void makeCredentialFromRequest_givenRsaType_andNoExisting_returnsRsaCredential() throws Exception {
    RsaCredentialValue rsaValue = new RsaCredentialValue(
        "public-key",
        PLAINTEXT_VALUE);

    RsaCredential credential =
        (RsaCredential) subject.makeNewCredentialVersion(
            "rsa",
            CREDENTIAL_NAME,
            rsaValue,
            null,
            null);
    assertThat(credential.getCredentialName().getName(), equalTo(CREDENTIAL_NAME));
    assertThat(credential.getPublicKey(), equalTo("public-key"));
    assertThat(credential.getPrivateKey(), equalTo(PLAINTEXT_VALUE));
  }

  @Test
  public void makeCredentialFromRequest_givenSshType_andNoExisting_returnsSshCredential() throws Exception {
    SshCredentialValue sshValue = new SshCredentialValue(
        "public-key",
        PLAINTEXT_VALUE,
        null);

    SshCredential credential =
        (SshCredential) subject.makeNewCredentialVersion(
            "ssh",
            CREDENTIAL_NAME,
            sshValue,
            null,
            null);
    assertThat(credential.getCredentialName().getName(), equalTo(CREDENTIAL_NAME));
    assertThat(credential.getPublicKey(), equalTo("public-key"));
    assertThat(credential.getPrivateKey(), equalTo(PLAINTEXT_VALUE));
  }

  @Test
  public void makeCredentialFromRequest_givenJsonType_andNoExisting_returnsJsonCredential() throws Exception {
    JsonCredentialValue jsonValue = new JsonCredentialValue(jsonValueMap);

    JsonCredential credential =
        (JsonCredential) subject.makeNewCredentialVersion(
            "json",
            CREDENTIAL_NAME,
            jsonValue,
            null,
            null);
    assertThat(credential.getCredentialName().getName(), equalTo(CREDENTIAL_NAME));
    assertThat(credential.getValue(), equalTo(jsonValueMap));
  }

  @Test
  public void makeCredentialFromRequest_givenUserType_andNoExisting_returnsUserCredential() throws Exception {
    UserCredentialValue userValue = new UserCredentialValue("username", PLAINTEXT_VALUE, "salt");

    UserCredential credential =
        (UserCredential) subject.makeNewCredentialVersion(
            "user",
            CREDENTIAL_NAME,
            userValue,
            null,
            null);
    assertThat(credential.getCredentialName().getName(), equalTo(CREDENTIAL_NAME));
    assertThat(credential.getUsername(), equalTo("username"));
    assertThat(credential.getPassword(), equalTo(PLAINTEXT_VALUE));
    assertThat(credential.getSalt(), equalTo("salt"));
  }
}
