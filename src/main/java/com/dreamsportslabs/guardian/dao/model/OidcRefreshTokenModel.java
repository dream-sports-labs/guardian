package com.dreamsportslabs.guardian.dao.model;

import java.util.List;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Builder
public class OidcRefreshTokenModel {
  private String tenantId;
  private String clientId;
  private String userId;
  private String refreshToken;
  private long refreshTokenExp;
  private List<String> scope;
  private String deviceName;
  private String ip;
}
