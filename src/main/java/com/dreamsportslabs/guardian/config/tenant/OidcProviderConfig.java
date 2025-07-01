package com.dreamsportslabs.guardian.config.tenant;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class OidcProviderConfig {
  private String tenantId;
  private String providerName;
  private String issuer;
  private String jwksUrl;
  private String tokenUrl;
  private String clientId;
  private String clientSecret;
  private String redirectUri;
  private Boolean isSslEnabled;
  private String userIdentifier;
  private Map<String, Object> audienceClaims;
}
