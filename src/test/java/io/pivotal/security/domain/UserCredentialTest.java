package io.pivotal.security.domain;

import com.greghaskins.spectrum.Spectrum;
import io.pivotal.security.entity.UserCredentialData;
import io.pivotal.security.service.Encryption;
import org.junit.runner.RunWith;

import java.util.UUID;

import static com.greghaskins.spectrum.Spectrum.beforeEach;
import static com.greghaskins.spectrum.Spectrum.describe;
import static com.greghaskins.spectrum.Spectrum.it;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(Spectrum.class)
public class UserCredentialTest {
  private UserCredential subject;
  private Encryptor encryptor;
  private final String CREDENTIAL_NAME = "/test/user";
  private final String USER_PASSWORD = "test-user-password";
  private final String SALT = "test-salt";
  private final String USERNAME = "test-username";
  private final UUID ENCRYPTION_KEY_UUID = UUID.randomUUID();
  private final byte[] ENCRYPTED_PASSWORD = "encrypted-user-password".getBytes();
  private final byte[] NONCE = "user-NONCE".getBytes();
  private UserCredential NO_EXISTING_CREDENTIAL = null;
  private UserCredentialData userCredentialData;

  {
    beforeEach(() -> {
      encryptor = mock(Encryptor.class);
    });

    describe("#getCredentialType", () -> {
      it("should return user type", () -> {
        subject = new UserCredential();
        assertThat(subject.getCredentialType(), equalTo("user"));
      });
    });

    describe("#getUsername", () -> {
      it("gets username from the delegate", () -> {
        subject = new UserCredential(
            new UserCredentialData(CREDENTIAL_NAME).setUsername("test-user"));
        assertThat(subject.getUsername(), equalTo("test-user"));
      });
    });

    describe("#setUsername", () -> {
      it("sets username on the delegate", () -> {
        UserCredentialData delegate = new UserCredentialData(CREDENTIAL_NAME);
        subject = new UserCredential(delegate);
        subject.setUsername("test-user");
        assertThat(delegate.getUsername(), equalTo("test-user"));
      });
    });

    describe("#getPassword", () -> {
      beforeEach(() -> {
        final Encryption encryption = new Encryption(ENCRYPTION_KEY_UUID, ENCRYPTED_PASSWORD, NONCE);
        when(encryptor.decrypt(encryption))
            .thenReturn(USER_PASSWORD);
        userCredentialData = new UserCredentialData()
            .setEncryptedValue(ENCRYPTED_PASSWORD)
            .setNonce(NONCE)
            .setEncryptionKeyUuid(ENCRYPTION_KEY_UUID);
        subject = new UserCredential(userCredentialData)
            .setEncryptor(encryptor);
      });

      it("should return decrypted password", () -> {
        assertThat(subject.getPassword(), equalTo(USER_PASSWORD));
      });

      it("should call decrypt once", () -> {
        subject.getPassword();
        verify(encryptor, times(1)).decrypt(any());
      });
    });

    describe("setPassword", () -> {
      beforeEach(() -> {
        when(encryptor.encrypt(eq(USER_PASSWORD)))
            .thenReturn(new Encryption(ENCRYPTION_KEY_UUID, ENCRYPTED_PASSWORD, NONCE));
        userCredentialData = new UserCredentialData(CREDENTIAL_NAME);
        subject = new UserCredential(userCredentialData)
            .setEncryptor(encryptor);
        subject.setPassword(USER_PASSWORD);
      });

      it("should encrypt provided password", () -> {
        verify(encryptor, times(1)).encrypt(eq(USER_PASSWORD));
      });

      it("sets encryption key uuid, encrypted value and the nonce on the delegate", () -> {
        subject.setPassword(USER_PASSWORD);

        assertThat(userCredentialData.getEncryptionKeyUuid(), equalTo(ENCRYPTION_KEY_UUID));
        assertThat(userCredentialData.getEncryptedValue(), equalTo(ENCRYPTED_PASSWORD));
        assertThat(userCredentialData.getNonce(), equalTo(NONCE));
      });
    });

    describe("#rotate", () -> {
      beforeEach(() -> {
        UUID oldEncryptionKeyUuid = UUID.randomUUID();
        byte[] oldEncryptedPassword = "old-encrypted-password".getBytes();
        byte[] oldNonce = "old-nonce".getBytes();
        userCredentialData = new UserCredentialData(CREDENTIAL_NAME)
            .setEncryptionKeyUuid(oldEncryptionKeyUuid)
            .setEncryptedValue(oldEncryptedPassword)
            .setNonce(oldNonce);
        subject = new UserCredential(userCredentialData)
            .setEncryptor(encryptor);
        when(encryptor.decrypt(new Encryption(oldEncryptionKeyUuid, oldEncryptedPassword, oldNonce)))
            .thenReturn(USER_PASSWORD);
        when(encryptor.encrypt(eq(USER_PASSWORD)))
            .thenReturn(new Encryption(ENCRYPTION_KEY_UUID, ENCRYPTED_PASSWORD, NONCE));
      });

      it("should re-encrypt the password with the new encryption key", () -> {
        subject.rotate();
        verify(encryptor).decrypt(any());
        verify(encryptor).encrypt(USER_PASSWORD);

        assertThat(userCredentialData.getEncryptionKeyUuid(), equalTo(ENCRYPTION_KEY_UUID));
        assertThat(userCredentialData.getEncryptedValue(), equalTo(ENCRYPTED_PASSWORD));
        assertThat(userCredentialData.getNonce(), equalTo(NONCE));
      });
    });
  }
}
