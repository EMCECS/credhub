package io.pivotal.security.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.greghaskins.spectrum.Spectrum;
import io.pivotal.security.CredentialManagerApp;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.Filter;
import java.util.Collections;

import static com.greghaskins.spectrum.Spectrum.*;
import static io.pivotal.security.helper.SpectrumHelper.autoTransactional;
import static io.pivotal.security.helper.SpectrumHelper.wireAndUnwire;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(Spectrum.class)
@SpringApplicationConfiguration(classes = CredentialManagerApp.class)
@WebAppConfiguration
@ActiveProfiles({"dev", "NoExpirationSymmetricKeySecurityConfiguration"})
public class SecurityConfigurationTest {

  @Autowired
  WebApplicationContext applicationContext;

  @Autowired
  Filter springSecurityFilterChain;

  @Autowired
  ObjectMapper serializingObjectMapper;

  private MockMvc mockMvc;

  {
    wireAndUnwire(this);
    autoTransactional(this);

    beforeEach(() -> {
      mockMvc = MockMvcBuilders
          .webAppContextSetup(applicationContext)
          .addFilter(springSecurityFilterChain)
          .build();
    });

    it("/info can be accessed without authentication", withoutAuthCheck("/info", "$.auth-server.url"));

    it("/health can be accessed without authentication", withoutAuthCheck("/health", "$.db.status"));

    it("denies other endpoints", () -> {
      mockMvc.perform(post("/api/v1/data/test"))
          .andExpect(status().isUnauthorized());
    });

    describe("with a token accepted by our security config", () -> {
      it("allows access", () -> {
        final MockHttpServletRequestBuilder post = post("/api/v1/data/test")
            .header("Authorization", "Bearer " + NoExpirationSymmetricKeySecurityConfiguration.EXPIRED_SYMMETRIC_KEY_JWT)
            .accept(MediaType.APPLICATION_JSON)
            .contentType(MediaType.APPLICATION_JSON)
            .content(serializingObjectMapper.writeValueAsBytes(Collections.singletonMap("type", "value")));

        mockMvc.perform(post)
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.type").value("value"))
            .andExpect(jsonPath("$.updated_at").exists())
            .andExpect(jsonPath("$.credential").exists());
      });
    });
  }

  private Spectrum.Block withoutAuthCheck(String path, String expectedJsonSpec) {
    return () -> {
      mockMvc.perform(get(path).accept(MediaType.APPLICATION_JSON))
          .andExpect(status().isOk())
          .andExpect(jsonPath(expectedJsonSpec).isNotEmpty());
    };
  }
}