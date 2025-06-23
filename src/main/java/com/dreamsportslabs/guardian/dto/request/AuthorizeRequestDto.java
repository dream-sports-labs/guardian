package com.dreamsportslabs.guardian.dto.request;

import static com.dreamsportslabs.guardian.constant.Constants.OIDC_CODE_CHALLENGE_METHOD_PLAIN;
import static com.dreamsportslabs.guardian.constant.Constants.OIDC_CODE_CHALLENGE_METHOD_S256;
import static com.dreamsportslabs.guardian.constant.Constants.OIDC_PROMPT_CONSENT;
import static com.dreamsportslabs.guardian.constant.Constants.OIDC_PROMPT_LOGIN;
import static com.dreamsportslabs.guardian.constant.Constants.OIDC_PROMPT_NONE;
import static com.dreamsportslabs.guardian.constant.Constants.OIDC_PROMPT_SELECT_ACCOUNT;
import static com.dreamsportslabs.guardian.exception.ErrorEnum.INVALID_REQUEST;

import jakarta.ws.rs.QueryParam;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import com.dreamsportslabs.guardian.constant.Constants;

@Data
public class AuthorizeRequestDto {
  @QueryParam(value = "response_type")
  private String responseType;

  @QueryParam(value = "scope")
  private String scope;

  @QueryParam(value = "client_id")
  private String clientId;

  @QueryParam(value = "redirect_uri")
  private String redirectUri;

  @QueryParam(value = "state")
  private String state;

  @QueryParam(value = "nonce")
  private String nonce;

  @QueryParam(value = "code_challenge")
  private String codeChallenge;

  @QueryParam(value = "code_challenge_method")
  private String codeChallengeMethod;

  @QueryParam(value = "prompt")
  private String prompt;

  @QueryParam(value = "login_hint")
  private String loginHint;

  public void validate() {
    if (StringUtils.isBlank(clientId)) {
      throw INVALID_REQUEST.getCustomException("client_id is required");
    }

    if (StringUtils.isBlank(scope)) {
      throw INVALID_REQUEST.getCustomException("scope is required");
    }

    if (StringUtils.isBlank(redirectUri)) {
      throw INVALID_REQUEST.getCustomException("redirect_uri is required");
    }

    if (StringUtils.isBlank(responseType)) {
      throw INVALID_REQUEST.getCustomException("response_type is required");
    }

    if (!Constants.oidcResponseTypes.contains(responseType)) {
      throw INVALID_REQUEST.getCustomException("Invalid response type");
    }

    if (StringUtils.isBlank(codeChallenge) && StringUtils.isNotBlank(codeChallengeMethod)) {
      throw INVALID_REQUEST.getCustomException(
          "code_challenge is required when code_challenge_method is provided");
    }

    if (StringUtils.isNotBlank(codeChallenge) && StringUtils.isBlank(codeChallengeMethod)) {
      throw INVALID_REQUEST.getCustomException(
          "code_challenge_method is required when code_challenge is provided");
    }

    if (StringUtils.isNotBlank(codeChallengeMethod)) {
      if (!codeChallengeMethod.equals(OIDC_CODE_CHALLENGE_METHOD_PLAIN)
          && !codeChallengeMethod.equals(OIDC_CODE_CHALLENGE_METHOD_S256)) {
        throw INVALID_REQUEST.getCustomException(
            "Invalid code_challenge_method. Must be one of: Plain, S256");
      }
    }

    if (StringUtils.isNotBlank(prompt)) {
      if (!prompt.equals(OIDC_PROMPT_LOGIN)
          && !prompt.equals(OIDC_PROMPT_CONSENT)
          && !prompt.equals(OIDC_PROMPT_NONE)
          && !prompt.equals(OIDC_PROMPT_SELECT_ACCOUNT)) {
        throw INVALID_REQUEST.getCustomException(
            "Invalid prompt value. Must be one of: login, consent, none, select_account");
      }
    }
  }
}
