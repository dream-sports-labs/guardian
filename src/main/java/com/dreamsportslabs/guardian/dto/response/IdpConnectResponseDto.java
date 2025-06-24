package com.dreamsportslabs.guardian.dto.response;

import com.dreamsportslabs.guardian.dao.model.IdpCredentialsModel;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class IdpConnectResponseDto {
  private String accessToken;
  private String refreshToken;
  private String idToken;
  private String tokenType;
  private Integer expiresIn;
  private Boolean isNewUser;
  private IdpCredentialsModel idpCredentials;
}
