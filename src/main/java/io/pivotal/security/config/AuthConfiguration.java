package io.pivotal.security.config;

import io.pivotal.security.oauth.AuditOAuth2AccessDeniedHandler;
import io.pivotal.security.oauth.AuditOAuth2AuthenticationExceptionHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.SecurityProperties;
import org.springframework.boot.autoconfigure.security.oauth2.resource.ResourceServerProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.config.annotation.web.configuration.EnableResourceServer;
import org.springframework.security.oauth2.config.annotation.web.configuration.ResourceServerConfigurerAdapter;
import org.springframework.security.oauth2.config.annotation.web.configurers.ResourceServerSecurityConfigurer;
import org.springframework.web.context.WebApplicationContext;

@Configuration
@EnableResourceServer
@EnableWebSecurity
public class AuthConfiguration extends ResourceServerConfigurerAdapter {

  @Autowired
  ResourceServerProperties resourceServerProperties;

  @Autowired
  AuditOAuth2AuthenticationExceptionHandler auditOAuth2AuthenticationExceptionHandler;

  @Autowired
  SecurityProperties securityProperties;

  @Autowired
  AuditOAuth2AccessDeniedHandler auditOAuth2AccessDeniedHandler;

  @Override
  public void configure(ResourceServerSecurityConfigurer resources) throws Exception {
    resources.resourceId(resourceServerProperties.getResourceId());
    resources.authenticationEntryPoint(auditOAuth2AuthenticationExceptionHandler);
    resources.accessDeniedHandler(auditOAuth2AccessDeniedHandler);
  }

  @Override
  public void configure(HttpSecurity http) throws Exception {
    http
        .authorizeRequests()
        .antMatchers("/info").permitAll()
        .antMatchers("/health").permitAll()
        .antMatchers("/api/v1/**").access("#oauth2.hasScope('credhub.read') and #oauth2.hasScope('credhub.write')")
        .and()
        .httpBasic();
//        .disable();
  }
}
