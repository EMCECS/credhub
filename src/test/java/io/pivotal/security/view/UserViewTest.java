package io.pivotal.security.view;

import com.greghaskins.spectrum.Spectrum;
import io.pivotal.security.domain.Encryptor;
import io.pivotal.security.domain.UserCredential;
import org.junit.runner.RunWith;

import java.util.UUID;

import static com.greghaskins.spectrum.Spectrum.beforeEach;
import static com.greghaskins.spectrum.Spectrum.it;
import static io.pivotal.security.helper.SpectrumHelper.json;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@RunWith(Spectrum.class)
public class UserViewTest {
  private Encryptor encryptor;

  {
    beforeEach(() -> {
      encryptor = mock(Encryptor.class);
      when(encryptor.decrypt(any())).thenReturn("test-password");
    });

    it("can create view from entity", () -> {
      UUID uuid = UUID.randomUUID();

      UserView actual = (UserView) UserView.fromEntity(
          new UserCredential("/foo")
              .setUuid(uuid)
              .setUsername("test-username"));
      assertThat(json(actual), equalTo("{"
          + "\"type\":\"user\","
          + "\"version_created_at\":null,"
          + "\"id\":\"" + uuid.toString() + "\","
          + "\"name\":\"/foo\","
          + "\"value\":{"
          + "\"username\":\"test-username\","
          + "\"password\":\"test-password\""
          + "}}"));
    });
  }
}
