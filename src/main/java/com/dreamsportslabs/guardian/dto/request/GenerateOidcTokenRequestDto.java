package com.dreamsportslabs.guardian.dto.request;

import io.vertx.core.json.JsonObject;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class GenerateOidcTokenRequestDto {
  private String userId;
  private String clientId;
  private String tenantId;
  private String nonce;
  private List<String> scope;
  private JsonObject userResponse;
  @Builder.Default private long iat = System.currentTimeMillis() / 1000;
  private String deviceName;
  private String ip;
}
