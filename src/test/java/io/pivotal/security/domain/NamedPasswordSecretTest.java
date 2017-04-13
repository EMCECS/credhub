package io.pivotal.security.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greghaskins.spectrum.Spectrum;
import io.pivotal.security.entity.AccessEntryData;
import io.pivotal.security.entity.NamedPasswordSecretData;
import io.pivotal.security.request.AccessControlEntry;
import io.pivotal.security.request.AccessControlOperation;
import io.pivotal.security.request.PasswordGenerationParameters;
import io.pivotal.security.service.Encryption;
import org.junit.runner.RunWith;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.google.common.collect.Lists.newArrayList;
import static com.greghaskins.spectrum.Spectrum.beforeEach;
import static com.greghaskins.spectrum.Spectrum.describe;
import static com.greghaskins.spectrum.Spectrum.it;
import static io.pivotal.security.helper.SpectrumHelper.itThrows;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.samePropertyValuesAs;
import static org.hamcrest.core.IsNull.notNullValue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(Spectrum.class)
public class NamedPasswordSecretTest {

  private static final List<AccessControlEntry> EMPTY_ENTRIES_LIST = new ArrayList<>();
  private static final PasswordGenerationParameters NO_PASSWORD_PARAMS = null;
  private static final NamedPasswordSecret NO_EXISTING_NAMED_PASSWORD_SECRET = null;
  private static final List<AccessControlEntry> NULL_ENTRIES_LIST = null;

  private Encryptor encryptor;
  private NamedPasswordSecret subject;
  private PasswordGenerationParameters generationParameters;
  private UUID canaryUuid;
  private NamedPasswordSecretData namedPasswordSecretData;

  {
    beforeEach(() -> {
      canaryUuid = UUID.randomUUID();
      generationParameters = new PasswordGenerationParameters();
      generationParameters.setExcludeLower(true);
      generationParameters.setIncludeSpecial(false);
      generationParameters.setLength(10);

      encryptor = mock(Encryptor.class);

      when(encryptor.encrypt(null)).thenReturn(new Encryption(canaryUuid, null, null));

      byte[] encryptedValue = "fake-encrypted-value".getBytes();
      byte[] nonce = "fake-nonce".getBytes();
      when(encryptor.encrypt("my-value"))
          .thenReturn(new Encryption(canaryUuid, encryptedValue, nonce));
      when(encryptor.decrypt(any(UUID.class), eq(encryptedValue), eq(nonce)))
          .thenReturn("my-value");

      String generationParametersJson = new ObjectMapper().writeValueAsString(generationParameters);
      byte[] encryptedParametersValue = "fake-encrypted-parameters".getBytes();
      byte[] parametersNonce = "fake-parameters-nonce".getBytes();
      when(encryptor.encrypt(generationParametersJson))
          .thenReturn(new Encryption(canaryUuid, encryptedParametersValue, parametersNonce));
      when(encryptor.decrypt(any(UUID.class), eq(encryptedParametersValue), eq(parametersNonce)))
          .thenReturn(generationParametersJson);

      subject = new NamedPasswordSecret("/Foo");
      subject.setEncryptor(encryptor);

    });

    it("returns type password", () -> {
      assertThat(subject.getSecretType(), equalTo("password"));
    });

    describe("with or without alternative names", () -> {
      beforeEach(() -> {
        namedPasswordSecretData = new NamedPasswordSecretData("/foo");
        subject = new NamedPasswordSecret(namedPasswordSecretData);
        subject.setEncryptor(encryptor);
      });

      it("sets the nonce and the encrypted value", () -> {
        subject.setPasswordAndGenerationParameters("my-value", null);
        assertThat(namedPasswordSecretData.getEncryptedValue(), notNullValue());
        assertThat(namedPasswordSecretData.getNonce(), notNullValue());
      });

      it("can decrypt values", () -> {
        subject.setPasswordAndGenerationParameters("my-value", generationParameters);

        assertThat(subject.getPassword(), equalTo("my-value"));

        assertThat(subject.getGenerationParameters().getLength(), equalTo(8));
        assertThat(subject.getGenerationParameters().isExcludeLower(), equalTo(true));
        assertThat(subject.getGenerationParameters().isExcludeUpper(), equalTo(false));
      });

      itThrows("when setting a value that is null", IllegalArgumentException.class, () -> {
        subject.setPasswordAndGenerationParameters(null, null);
      });

      it("sets the parametersNonce and the encryptedGenerationParameters", () -> {
        subject.setPasswordAndGenerationParameters("my-value", generationParameters);
        assertThat(namedPasswordSecretData.getEncryptedGenerationParameters(), notNullValue());
        assertThat(namedPasswordSecretData.getParametersNonce(), notNullValue());
      });

      it("should set encrypted generation parameters and nonce to null if parameters are null",
          () -> {
            subject = new NamedPasswordSecret("password-with-null-parameters");
            subject.setEncryptor(encryptor);
            subject.setPasswordAndGenerationParameters("my-value", null);
            assertThat(namedPasswordSecretData.getEncryptedGenerationParameters(), nullValue());
            assertThat(namedPasswordSecretData.getParametersNonce(), nullValue());
          });
    });

    describe("#copyInto", () -> {
      it("should copy the correct properties into the other object", () -> {
        Instant frozenTime = Instant.ofEpochSecond(1400000000L);
        UUID uuid = UUID.randomUUID();

        PasswordGenerationParameters parameters = new PasswordGenerationParameters();
        parameters.setExcludeNumber(true);
        parameters.setExcludeLower(true);
        parameters.setExcludeUpper(false);

        String generationParametersJson = new ObjectMapper().writeValueAsString(parameters);
        byte[] encryptedParametersValue = "fake-encrypted-parameters".getBytes();
        byte[] parametersNonce = "fake-parameters-nonce".getBytes();
        byte[] encryptedPasswordValue = "fake-encrypted-password".getBytes();
        byte[] passwordNonce = "password-nonce".getBytes();
        when(encryptor.encrypt(generationParametersJson))
            .thenReturn(new Encryption(canaryUuid, encryptedParametersValue, parametersNonce));
        when(encryptor.decrypt(any(UUID.class), eq(encryptedParametersValue), eq(parametersNonce)))
            .thenReturn(generationParametersJson);
        when(encryptor.encrypt("my-password"))
            .thenReturn(new Encryption(canaryUuid, encryptedPasswordValue, passwordNonce));
        when(encryptor.decrypt(any(UUID.class), eq(encryptedPasswordValue), eq(passwordNonce)))
            .thenReturn("my-password");

        namedPasswordSecretData = new NamedPasswordSecretData("/foo");
        subject = new NamedPasswordSecret(namedPasswordSecretData);
        subject.setEncryptor(encryptor);
        subject.setPasswordAndGenerationParameters("my-password", parameters);
        subject.setUuid(uuid);
        subject.setVersionCreatedAt(frozenTime);

        NamedPasswordSecret copy = new NamedPasswordSecret();
        subject.copyInto(copy);

        assertThat(copy.getName(), equalTo("/foo"));
        assertThat(copy.getPassword(), equalTo("my-password"));
        assertThat(copy.getGenerationParameters(),
            samePropertyValuesAs(subject.getGenerationParameters()));

        assertThat(copy.getUuid(), not(equalTo(uuid)));
        assertThat(copy.getVersionCreatedAt(), not(equalTo(frozenTime)));

        verify(encryptor, times(2)).encrypt(any());
        verify(encryptor, times(5)).decrypt(any(), any(), any());
      });
    });

    describe(".createNewVersion", () -> {
      beforeEach(() -> {
        byte[] encryptedValue = "new-fake-encrypted".getBytes();
        byte[] nonce = "new-fake-nonce".getBytes();
        when(encryptor.encrypt("new password"))
            .thenReturn(new Encryption(canaryUuid, encryptedValue, nonce));
        when(encryptor.decrypt(any(UUID.class), eq(encryptedValue), eq(nonce)))
            .thenReturn("new password");

        namedPasswordSecretData = new NamedPasswordSecretData("/existingName");
        namedPasswordSecretData.setEncryptedValue("old encrypted value".getBytes());
        subject = new NamedPasswordSecret(namedPasswordSecretData);
        subject.setEncryptor(encryptor);

        ArrayList<AccessControlOperation> operations = newArrayList(AccessControlOperation.READ,
            AccessControlOperation.WRITE);
        List<AccessEntryData> accessControlEntries = newArrayList(
            new AccessEntryData(subject.getSecretName(),
                new AccessControlEntry("Bob", operations)));
        subject.setAccessControlList(accessControlEntries);
      });

      it("copies values from existing, except password", () -> {
        NamedPasswordSecret newSecret = NamedPasswordSecret
            .createNewVersion(subject, "anything I AM IGNORED", "new password", NO_PASSWORD_PARAMS,
                encryptor, EMPTY_ENTRIES_LIST);

        assertThat(newSecret.getName(), equalTo("/existingName"));
        assertThat(newSecret.getPassword(), equalTo("new password"));

        assertThat(newSecret.getSecretName().getAccessControlList(), hasSize(0));
      });

      it("creates new if no existing", () -> {
        NamedPasswordSecret newSecret = NamedPasswordSecret.createNewVersion(
            NO_EXISTING_NAMED_PASSWORD_SECRET,
            "/newName",
            "new password",
            NO_PASSWORD_PARAMS,
            encryptor,
            EMPTY_ENTRIES_LIST);

        assertThat(newSecret.getName(), equalTo("/newName"));
        assertThat(newSecret.getPassword(), equalTo("new password"));
      });

      it("ignores ACEs if not provided", () -> {
        NamedPasswordSecret newSecret = NamedPasswordSecret
            .createNewVersion(subject, "anything I AM IGNORED", "new password", NO_PASSWORD_PARAMS,
                encryptor, NULL_ENTRIES_LIST);
        assertThat(newSecret.getSecretName().getAccessControlList(), hasSize(0));
      });
    });
  }
}
