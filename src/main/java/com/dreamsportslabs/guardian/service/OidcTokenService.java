package com.dreamsportslabs.guardian.service;

import static com.dreamsportslabs.guardian.constant.Constants.IS_NEW_USER;
import static com.dreamsportslabs.guardian.constant.Constants.JWT_CLAIMS_EXP;
import static com.dreamsportslabs.guardian.constant.Constants.JWT_CLAIMS_IAT;
import static com.dreamsportslabs.guardian.constant.Constants.JWT_CLAIMS_ISS;
import static com.dreamsportslabs.guardian.constant.Constants.JWT_CLAIMS_RFT_ID;
import static com.dreamsportslabs.guardian.constant.Constants.JWT_CLAIMS_SUB;
import static com.dreamsportslabs.guardian.constant.Constants.TOKEN_TYPE;
import static com.dreamsportslabs.guardian.exception.ErrorEnum.INTERNAL_SERVER_ERROR;
import static com.dreamsportslabs.guardian.exception.OidcErrorEnum.INVALID_CLIENT;
import static com.dreamsportslabs.guardian.exception.OidcErrorEnum.INVALID_GRANT;
import static com.dreamsportslabs.guardian.exception.OidcErrorEnum.INVALID_REQUEST;
import static com.dreamsportslabs.guardian.utils.Utils.getRftId;

import com.dreamsportslabs.guardian.config.tenant.OidcConfig;
import com.dreamsportslabs.guardian.config.tenant.TenantConfig;
import com.dreamsportslabs.guardian.config.tenant.TokenConfig;
import com.dreamsportslabs.guardian.constant.OidcCodeChallengeMethod;
import com.dreamsportslabs.guardian.dao.OidcTokenDao;
import com.dreamsportslabs.guardian.dao.model.CodeSessionModel;
import com.dreamsportslabs.guardian.dao.model.OidcRefreshTokenModel;
import com.dreamsportslabs.guardian.dto.request.GenerateOidcTokenRequestDto;
import com.dreamsportslabs.guardian.dto.request.TokenRequestDto;
import com.dreamsportslabs.guardian.dto.response.TokenResponseDto;
import com.dreamsportslabs.guardian.registry.Registry;
import com.dreamsportslabs.guardian.utils.Utils;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import jakarta.ws.rs.core.MultivaluedMap;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class OidcTokenService {

  private final ClientService clientService;
  private final CodeSessionService codeSessionService;
  private final UserService userService;
  private final TokenIssuer tokenIssuer;

  private final OidcTokenDao oidcTokenDao;

  private final Registry registry;

  public Single<TokenResponseDto> getOidcTokens(
      TokenRequestDto requestDto,
      String tenantId,
      String authorizationHeader,
      MultivaluedMap<String, String> headers) {
    return authenticateClient(requestDto, tenantId, authorizationHeader)
        .doOnSuccess(requestDto::setClientId)
        .ignoreElement()
        .andThen(validateCode(requestDto, tenantId))
        .doOnSuccess(
            __ -> codeSessionService.deleteCodeSession(requestDto.getCode(), tenantId).subscribe())
        .flatMap(
            codeSessionModel ->
                userService
                    .getOidcUser(getUserFilters(codeSessionModel), headers, tenantId)
                    .flatMap(
                        userResponse ->
                            generateAndStoreOidcTokens(
                                getGenerateOidcTokenRequestDto(
                                    codeSessionModel, tenantId, userResponse))));
  }

  private Single<String> authenticateClient(
      TokenRequestDto requestDto, String tenantId, String authorizationHeader) {
    if (authorizationHeader != null) {
      try {
        String[] clientCredentials = Utils.getCredentialsFromAuthHeader(authorizationHeader);
        return clientService
            .authenticateClient(clientCredentials[0], clientCredentials[1], tenantId)
            .andThen(Single.just(clientCredentials[0]));
      } catch (Exception e) {
        Map<String, String> additionalHeaders = new HashMap<>();
        additionalHeaders.put("WWW-Authenticate", "Basic realm=\"Guardian\"");
        return Single.error(INVALID_CLIENT.getJsonCustomException(null, additionalHeaders));
      }
    } else {
      return clientService
          .authenticateClient(requestDto.getClientId(), requestDto.getClientSecret(), tenantId)
          .andThen(Single.just(requestDto.getClientId()));
    }
  }

  private Single<CodeSessionModel> validateCode(TokenRequestDto requestDto, String tenantId) {
    return codeSessionService
        .getCodeSession(requestDto.getCode(), tenantId)
        .onErrorResumeNext(
            err ->
                Single.error(
                    INVALID_GRANT.getJsonCustomException("The authorization_code is invalid")))
        .filter(
            codeSessionModel ->
                codeSessionModel.getClient().getClientId().equals(requestDto.getClientId()))
        .switchIfEmpty(
            Single.error(INVALID_GRANT.getJsonCustomException("The authorization_code is invalid")))
        .filter(
            codeSessionModel ->
                codeSessionModel.getRedirectUri().equals(requestDto.getRedirectUri()))
        .switchIfEmpty(
            Single.error(INVALID_GRANT.getJsonCustomException("The redirect_uri is invalid")))
        .filter(
            codeSessionModel ->
                pkceTest(
                    requestDto.getCodeVerifier(),
                    codeSessionModel.getCodeChallenge(),
                    codeSessionModel.getCodeChallengeMethod()))
        .switchIfEmpty(
            Single.error(INVALID_GRANT.getJsonCustomException("The code_verifier is invalid")));
  }

  private Single<TokenResponseDto> generateAndStoreOidcTokens(
      GenerateOidcTokenRequestDto generateOidcTokenRequestDto) {
    TenantConfig tenantConfig =
        registry.get(generateOidcTokenRequestDto.getTenantId(), TenantConfig.class);
    TokenConfig tokenConfig = tenantConfig.getTokenConfig();
    OidcConfig oidcConfig = tenantConfig.getOidcConfig();
    return generateOidcTokens(generateOidcTokenRequestDto)
        .flatMap(
            tokenResponseDto -> {
              OidcRefreshTokenModel oidcRefreshTokenModel =
                  OidcRefreshTokenModel.builder()
                      .tenantId(generateOidcTokenRequestDto.getTenantId())
                      .clientId(generateOidcTokenRequestDto.getClientId())
                      .userId(generateOidcTokenRequestDto.getUserId())
                      .refreshToken(tokenResponseDto.getRefreshToken())
                      .refreshTokenExp(
                          generateOidcTokenRequestDto.getIat()
                              + tokenConfig.getRefreshTokenExpiry())
                      .scope(generateOidcTokenRequestDto.getScope())
                      .deviceName(generateOidcTokenRequestDto.getDeviceName())
                      .ip(generateOidcTokenRequestDto.getIp())
                      .build();
              return oidcTokenDao
                  .saveOidcRefreshToken(oidcRefreshTokenModel)
                  .andThen(Single.just(tokenResponseDto));
            });
  }

  private Single<TokenResponseDto> generateOidcTokens(
      GenerateOidcTokenRequestDto generateOidcTokenDto) {
    TenantConfig tenantConfig =
        registry.get(generateOidcTokenDto.getTenantId(), TenantConfig.class);
    TokenConfig tokenConfig = tenantConfig.getTokenConfig();
    OidcConfig oidcConfig = tenantConfig.getOidcConfig();
    String refreshToken = tokenIssuer.generateRefreshToken();
    Map<String, Object> commonTokenClaims = new HashMap<>();
    commonTokenClaims.put(JWT_CLAIMS_SUB, generateOidcTokenDto.getUserId());
    commonTokenClaims.put(JWT_CLAIMS_IAT, generateOidcTokenDto.getIat());
    commonTokenClaims.put(JWT_CLAIMS_ISS, oidcConfig.getIssuer());
    Map<String, Object> accessTokenClaims = new HashMap<>(commonTokenClaims),
        idTokenClaims = new HashMap<>(commonTokenClaims);
    accessTokenClaims.put(JWT_CLAIMS_RFT_ID, getRftId(refreshToken));
    accessTokenClaims.put(
        JWT_CLAIMS_EXP, generateOidcTokenDto.getIat() + tokenConfig.getAccessTokenExpiry());
    return Single.zip(
        tokenIssuer.generateAccessToken(accessTokenClaims, generateOidcTokenDto.getTenantId()),
        tokenIssuer.generateIdToken(
            idTokenClaims,
            generateOidcTokenDto.getUserResponse(),
            generateOidcTokenDto.getTenantId()),
        (accessToken, idToken) ->
            new TokenResponseDto(
                accessToken,
                refreshToken,
                idToken,
                TOKEN_TYPE,
                tokenConfig.getAccessTokenExpiry(),
                generateOidcTokenDto.getUserResponse().getBoolean(IS_NEW_USER)));
  }

  private Boolean pkceTest(
      String codeVerifier, String codeChallenge, OidcCodeChallengeMethod codeChallengeMethod) {
    if (codeChallenge == null && codeChallengeMethod == null) return true;
    if (codeChallenge != null && codeChallengeMethod != null && codeVerifier == null)
      throw INVALID_REQUEST.getJsonCustomException("code_verifier is required");
    if (codeChallengeMethod.equals(OidcCodeChallengeMethod.plain)) {
      return codeChallenge.equals(codeVerifier);
    } else if (codeChallengeMethod.equals(OidcCodeChallengeMethod.S256)) {
      return codeChallenge.equals(hashAndEncodeString(codeVerifier));
    }
    return false;
  }

  private String hashAndEncodeString(String input) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      byte[] hash = digest.digest(input.getBytes(StandardCharsets.US_ASCII));
      return new String(Base64.getEncoder().withoutPadding().encode(hash));
    } catch (Exception e) {
      log.error("Error hashing and encoding code verifier string: {}", e.getMessage());
      throw INTERNAL_SERVER_ERROR.getException();
    }
  }

  private GenerateOidcTokenRequestDto getGenerateOidcTokenRequestDto(
      CodeSessionModel codeSessionModel, String tenantId, JsonObject userResponse) {
    return GenerateOidcTokenRequestDto.builder()
        .clientId(codeSessionModel.getClient().getClientId())
        .userId(codeSessionModel.getUserId())
        .scope(codeSessionModel.getConsentedScopes())
        .userResponse(userResponse)
        .nonce(codeSessionModel.getNonce())
        .tenantId(tenantId)
        .iat(System.currentTimeMillis() / 1000)
        .build();
  }

  private Map<String, String> getUserFilters(CodeSessionModel codeSessionModel) {
    Map<String, String> filters = new HashMap<>();
    filters.put("userId", codeSessionModel.getUserId());
    return filters;
  }
}
