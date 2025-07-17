package com.dreamsportslabs.guardian.dao.model;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.jackson.Jacksonized;

@Getter
@Setter
@Builder
@Jacksonized
public class OidcRefreshTokenModel {
  private String tenantId;
  private String clientId;
  private String userId;
  private Boolean isActive;
  private String refreshToken;
  private long refreshTokenExp;
  private List<String> scope;
  private String deviceName;
  private String ip;
}
