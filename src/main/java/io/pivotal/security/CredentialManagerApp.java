package io.pivotal.security;

import static io.pivotal.security.util.TimeModuleFactory.createTimeModule;

import com.fasterxml.jackson.databind.Module;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;
import io.pivotal.security.config.AuthServerProperties;
import io.pivotal.security.config.JsonContextFactory;
import io.pivotal.security.util.CurrentTimeProvider;
import java.security.Security;
import org.bouncycastle.asn1.oiw.OIWObjectIdentifiers;
import org.bouncycastle.asn1.x509.AlgorithmIdentifier;
import org.bouncycastle.cert.X509ExtensionUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.bouncycastle.operator.OperatorCreationException;
import org.bouncycastle.operator.bc.BcDigestCalculatorProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;

@SpringBootApplication
@EnableConfigurationProperties({AuthServerProperties.class})
@EnableJpaAuditing(dateTimeProviderRef = "currentTimeProvider")
@EnableAspectJAutoProxy
public class CredentialManagerApp {

  public static void main(String[] args) {
    SpringApplication.run(CredentialManagerApp.class, args);
  }

  @Bean
  public Module javaTimeModule() {
    return createTimeModule();
  }

  @Bean
  public JsonContextFactory jsonContextFactory() {
    return new JsonContextFactory();
  }

  @Bean(name = "currentTimeProvider")
  public CurrentTimeProvider currentTimeProvider() {
    return new CurrentTimeProvider();
  }

  @Bean
  public Jackson2ObjectMapperBuilder jacksonBuilder(Module javaTimeModule) {
    Jackson2ObjectMapperBuilder builder = new Jackson2ObjectMapperBuilder();
    builder.modules(javaTimeModule);
    builder.failOnUnknownProperties(true);
    builder.propertyNamingStrategy(PropertyNamingStrategy.SNAKE_CASE);
    return builder;
  }

  @Bean
  public BouncyCastleProvider bouncyCastleProvider() {
    BouncyCastleProvider bouncyCastleProvider = new BouncyCastleProvider();
    Security.addProvider(bouncyCastleProvider);
    return bouncyCastleProvider;
  }

  @Bean
  public X509ExtensionUtils x509ExtensionUtils() throws OperatorCreationException {
    return new X509ExtensionUtils(new BcDigestCalculatorProvider().get(
        AlgorithmIdentifier.getInstance(OIWObjectIdentifiers.idSHA1)));
  }
}
