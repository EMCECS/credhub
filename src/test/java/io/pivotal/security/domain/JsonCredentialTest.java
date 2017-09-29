package io.pivotal.security.domain;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pivotal.security.entity.JsonCredentialData;
import io.pivotal.security.exceptions.ParameterizedValidationException;
import io.pivotal.security.service.Encryption;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static junit.framework.TestCase.fail;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.core.IsEqual.equalTo;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class JsonCredentialTest {

  private JsonCredential subject;
  private Map<String, Object> value;

  private JsonCredentialData jsonCredentialData;

  @Before
  public void beforeEach() throws JsonProcessingException {
    Map<String, Object> nested = new HashMap<>();
    nested.put("key", "value");

    value = new HashMap<>();
    value.put("simple", "just-a-string");
    value.put("complex", nested);

    String serializedValue = new ObjectMapper().writeValueAsString(value);

    Encryptor encryptor = mock(Encryptor.class);
    byte[] encryptedValue = "fake-encrypted-value".getBytes();
    byte[] nonce = "fake-nonce".getBytes();
    UUID canaryUuid = UUID.randomUUID();
    final Encryption encryption = new Encryption(canaryUuid, encryptedValue, nonce);
    when(encryptor.encrypt(serializedValue))
        .thenReturn(encryption);
    when(encryptor.decrypt(encryption))
        .thenReturn(serializedValue);

    jsonCredentialData = new JsonCredentialData("Foo");
    subject = new JsonCredential(jsonCredentialData).setEncryptor(encryptor);
  }

  @Test
  public void getCredentialType_returnsCorrectType() {
    assertThat(subject.getCredentialType(), equalTo("json"));
  }

  @Test
  public void setValue_setsEncryptedValueAndNonce() {
    subject.setValue(value);

    assertThat(jsonCredentialData.getEncryptedValue(), notNullValue());
    assertThat(jsonCredentialData.getNonce(), notNullValue());
  }

  @Test
  public void getValue_decryptsValue() {
    subject.setValue(value);

    assertThat(subject.getValue(), equalTo(value));
  }

  @Test
  public void setValue_whenValueIsNull_throwsException() {
    try {
      subject.setValue((Map) null);
      fail("should throw");
    } catch (ParameterizedValidationException e) {
      assertThat(e.getMessage(), equalTo("error.missing_value"));
    }
  }
}
