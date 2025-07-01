package com.dreamsportslabs.guardian.dto.response;

import com.dreamsportslabs.guardian.dao.model.IdpCredentialsModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.vertx.core.json.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IdpConnectResponseDto {
  private String code;
  private String accessToken;
  private String refreshToken;
  private String idToken;
  private String tokenType;
  private Integer expiresIn;
  private Boolean isNewUser;
  private IdpCredentialsModel idpCredentials;

  public static IdpConnectResponseDto fromResponse(
      JsonObject responseBody, IdpCredentialsModel idpTokens) {
    return new IdpConnectResponseDto(
        responseBody.getString("code"),
        responseBody.getString("accessToken"),
        responseBody.getString("refreshToken"),
        responseBody.getString("idToken"),
        responseBody.getString("tokenType"),
        responseBody.getInteger("expiresIn"),
        responseBody.getBoolean("isNewUser", false),
        idpTokens);
  }
}
