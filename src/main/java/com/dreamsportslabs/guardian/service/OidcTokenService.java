package com.dreamsportslabs.guardian.service;

import static com.dreamsportslabs.guardian.constant.Constants.JWT_CLAIMS_AUD;
import static com.dreamsportslabs.guardian.constant.Constants.JWT_CLAIMS_CLIENT_ID;
import static com.dreamsportslabs.guardian.constant.Constants.JWT_CLAIMS_EXP;
import static com.dreamsportslabs.guardian.constant.Constants.JWT_CLAIMS_IAT;
import static com.dreamsportslabs.guardian.constant.Constants.JWT_CLAIMS_ISS;
import static com.dreamsportslabs.guardian.constant.Constants.JWT_CLAIMS_JTI;
import static com.dreamsportslabs.guardian.constant.Constants.JWT_CLAIMS_RFT_ID;
import static com.dreamsportslabs.guardian.constant.Constants.JWT_CLAIMS_SCOPE;
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
import com.dreamsportslabs.guardian.dao.model.ClientScopeModel;
import com.dreamsportslabs.guardian.dao.model.CodeSessionModel;
import com.dreamsportslabs.guardian.dao.model.OidcRefreshTokenModel;
import com.dreamsportslabs.guardian.dto.request.GenerateOidcTokenDto;
import com.dreamsportslabs.guardian.dto.request.TokenRequestDto;
import com.dreamsportslabs.guardian.dto.response.OidcTokenResponseDto;
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
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class OidcTokenService {

  private final ClientService clientService;
  private final CodeSessionService codeSessionService;
  private final ClientScopeService clientScopeService;
  private final UserService userService;
  private final TokenIssuer tokenIssuer;

  private final OidcTokenDao oidcTokenDao;

  private final Registry registry;

  public Single<OidcTokenResponseDto> getOidcTokens(
      TokenRequestDto requestDto,
      String tenantId,
      String authorizationHeader,
      MultivaluedMap<String, String> headers) {
    return switch (requestDto.getOidcGrantType()) {
      case AUTHORIZATION_CODE -> authorizationCodeFlow(
          requestDto, tenantId, authorizationHeader, headers);
      case CLIENT_CREDENTIALS -> clientCredentialsFlow(
          requestDto, tenantId, authorizationHeader, headers);
      case REFRESH_TOKEN -> refreshTokenFlow(requestDto, tenantId, authorizationHeader, headers);
      default -> Single.error(INVALID_GRANT.getException());
    };
  }

  private Single<OidcTokenResponseDto> authorizationCodeFlow(
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
                    .getOidcUser(getUserFilters(codeSessionModel.getUserId()), headers, tenantId)
                    .flatMap(
                        userResponse -> {
                          GenerateOidcTokenDto generateOidcTokenDto =
                              getGenerateOidcTokenDtoForAuthorizationCode(
                                  codeSessionModel, tenantId, userResponse);
                          return generateOidcTokensForAuthorizationCodeFlow(generateOidcTokenDto)
                              .flatMap(
                                  tokenResponseDto -> {
                                    OidcRefreshTokenModel refreshTokenModel =
                                        getOidcRefreshTokenModel(
                                            tokenResponseDto, generateOidcTokenDto);
                                    return oidcTokenDao
                                        .saveOidcRefreshToken(refreshTokenModel)
                                        .andThen(Single.just(tokenResponseDto));
                                  });
                        }));
  }

  private Single<OidcTokenResponseDto> clientCredentialsFlow(
      TokenRequestDto requestDto,
      String tenantId,
      String authorizationHeader,
      MultivaluedMap<String, String> headers) {
    return authenticateClient(requestDto, tenantId, authorizationHeader)
        .doOnSuccess(requestDto::setClientId)
        .ignoreElement()
        .andThen(getAllowedScopes(requestDto.getClientId(), tenantId, requestDto.getScope()))
        .flatMap(
            allowedScopes ->
                generateOidcTokensForClientCredentialsFlow(
                    getGenerateOidcTokenDtoForClientCredentials(
                        requestDto.getClientId(), allowedScopes, tenantId)));
  }

  private Single<OidcTokenResponseDto> refreshTokenFlow(
      TokenRequestDto requestDto,
      String tenantId,
      String authorizationHeader,
      MultivaluedMap<String, String> headers) {
    return authenticateClient(requestDto, tenantId, authorizationHeader)
        .doOnSuccess(requestDto::setClientId)
        .ignoreElement()
        .andThen(validateRefreshToken(requestDto, tenantId))
        .flatMap(
            oidcRefreshTokenModel -> {
              List<String> scopes =
                  getValidScopes(oidcRefreshTokenModel.getScope(), requestDto.getScope());
              GenerateOidcTokenDto generateOidcTokenDto =
                  getGenerateOidcTokenDtoForRefreshTokenFlow(
                      requestDto.getClientId(),
                      oidcRefreshTokenModel.getUserId(),
                      tenantId,
                      oidcRefreshTokenModel.getScope());
              return generateOidcTokensForRefreshTokenFlow(
                  generateOidcTokenDto, requestDto.getRefreshToken());
            });
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

  private Single<OidcRefreshTokenModel> validateRefreshToken(
      TokenRequestDto requestDto, String tenantId) {
    return oidcTokenDao
        .getOidcRefreshToken(tenantId, requestDto.getClientId(), requestDto.getRefreshToken())
        .switchIfEmpty(
            Single.error(INVALID_GRANT.getJsonCustomException("The refresh_token is invalid")))
        .filter(OidcRefreshTokenModel::getIsActive)
        .switchIfEmpty(
            Single.error(INVALID_GRANT.getJsonCustomException("The refresh_token is inactive")))
        .filter(
            oidcRefreshTokenModel ->
                oidcRefreshTokenModel.getRefreshTokenExp() < (System.currentTimeMillis() / 1000))
        .switchIfEmpty(
            Single.error(INVALID_GRANT.getJsonCustomException("The refresh_token is inactive")))
        .map(oidcRefreshTokenModel -> oidcRefreshTokenModel);
  }

  private Single<List<String>> getAllowedScopes(
      String clientId, String tenantId, String requestScopes) {
    return clientScopeService
        .getClientScopes(clientId, tenantId)
        .map(
            clientScopeModels -> {
              List<String> allowedScopes =
                  clientScopeModels.stream().map(ClientScopeModel::getScope).toList();
              return getValidScopes(allowedScopes, requestScopes);
            });
  }

  private List<String> getValidScopes(List<String> allowedScopes, String requestScopes) {
    List<String> requestedScopes =
        requestScopes == null ? List.of() : List.of(requestScopes.split(" "));
    for (String scope : requestedScopes) {
      if (!allowedScopes.contains(scope)) {
        throw INVALID_REQUEST.getJsonCustomException(
            "The requested scope " + scope + " is not allowed");
      }
    }
    return requestedScopes;
  }

  private Single<OidcTokenResponseDto> generateOidcTokensForAuthorizationCodeFlow(
      GenerateOidcTokenDto generateOidcTokenDto) {
    TenantConfig tenantConfig =
        registry.get(generateOidcTokenDto.getTenantId(), TenantConfig.class);
    TokenConfig tokenConfig = tenantConfig.getTokenConfig();
    OidcConfig oidcConfig = tenantConfig.getOidcConfig();
    String refreshToken = tokenIssuer.generateRefreshToken();
    Map<String, Object> commonTokenClaims = new HashMap<>();
    commonTokenClaims.put(JWT_CLAIMS_AUD, generateOidcTokenDto.getClientId());
    commonTokenClaims.put(JWT_CLAIMS_IAT, generateOidcTokenDto.getIat());
    commonTokenClaims.put(JWT_CLAIMS_ISS, oidcConfig.getIssuer());
    commonTokenClaims.put(JWT_CLAIMS_SUB, generateOidcTokenDto.getUserId());
    Map<String, Object> accessTokenClaims = new HashMap<>(commonTokenClaims),
        idTokenClaims = new HashMap<>(commonTokenClaims);
    accessTokenClaims.put(JWT_CLAIMS_CLIENT_ID, generateOidcTokenDto.getClientId());
    accessTokenClaims.put(JWT_CLAIMS_JTI, RandomStringUtils.randomAlphanumeric(32));
    accessTokenClaims.put(JWT_CLAIMS_RFT_ID, getRftId(refreshToken));
    accessTokenClaims.put(
        JWT_CLAIMS_EXP, generateOidcTokenDto.getIat() + tokenConfig.getAccessTokenExpiry());
    accessTokenClaims.put(JWT_CLAIMS_SCOPE, String.join(" ", generateOidcTokenDto.getScope()));
    idTokenClaims.put(
        JWT_CLAIMS_EXP, generateOidcTokenDto.getIat() + tokenConfig.getIdTokenExpiry());

    return Single.zip(
        tokenIssuer.generateAccessToken(accessTokenClaims, generateOidcTokenDto.getTenantId()),
        tokenIssuer.generateIdToken(
            idTokenClaims,
            generateOidcTokenDto.getUserResponse(),
            generateOidcTokenDto.getTenantId()),
        (accessToken, idToken) ->
            OidcTokenResponseDto.builder()
                .accessToken(accessToken)
                .idToken(idToken)
                .refreshToken(refreshToken)
                .tokenType(TOKEN_TYPE)
                .expiresIn(tokenConfig.getAccessTokenExpiry())
                .build());
  }

  private Single<OidcTokenResponseDto> generateOidcTokensForClientCredentialsFlow(
      GenerateOidcTokenDto generateOidcTokenDto) {
    TenantConfig tenantConfig =
        registry.get(generateOidcTokenDto.getTenantId(), TenantConfig.class);
    TokenConfig tokenConfig = tenantConfig.getTokenConfig();
    OidcConfig oidcConfig = tenantConfig.getOidcConfig();
    Map<String, Object> accessTokenClaims = new HashMap<>();
    accessTokenClaims.put(JWT_CLAIMS_AUD, generateOidcTokenDto.getClientId());
    accessTokenClaims.put(JWT_CLAIMS_IAT, generateOidcTokenDto.getIat());
    accessTokenClaims.put(JWT_CLAIMS_ISS, oidcConfig.getIssuer());
    accessTokenClaims.put(JWT_CLAIMS_SUB, generateOidcTokenDto.getUserId());
    accessTokenClaims.put(JWT_CLAIMS_CLIENT_ID, generateOidcTokenDto.getClientId());
    accessTokenClaims.put(JWT_CLAIMS_JTI, RandomStringUtils.randomAlphanumeric(32));
    accessTokenClaims.put(
        JWT_CLAIMS_EXP, generateOidcTokenDto.getIat() + tokenConfig.getAccessTokenExpiry());
    accessTokenClaims.put(JWT_CLAIMS_SCOPE, String.join(" ", generateOidcTokenDto.getScope()));
    return tokenIssuer
        .generateAccessToken(accessTokenClaims, generateOidcTokenDto.getTenantId())
        .map(
            accessToken ->
                OidcTokenResponseDto.builder()
                    .accessToken(accessToken)
                    .tokenType(TOKEN_TYPE)
                    .expiresIn(tokenConfig.getAccessTokenExpiry())
                    .build());
  }

  private Single<OidcTokenResponseDto> generateOidcTokensForRefreshTokenFlow(
      GenerateOidcTokenDto generateOidcTokenDto, String refreshToken) {
    TenantConfig tenantConfig =
        registry.get(generateOidcTokenDto.getTenantId(), TenantConfig.class);
    TokenConfig tokenConfig = tenantConfig.getTokenConfig();
    OidcConfig oidcConfig = tenantConfig.getOidcConfig();
    Map<String, Object> accessTokenClaims = new HashMap<>();
    accessTokenClaims.put(JWT_CLAIMS_AUD, generateOidcTokenDto.getClientId());
    accessTokenClaims.put(JWT_CLAIMS_IAT, generateOidcTokenDto.getIat());
    accessTokenClaims.put(JWT_CLAIMS_ISS, oidcConfig.getIssuer());
    accessTokenClaims.put(JWT_CLAIMS_SUB, generateOidcTokenDto.getUserId());
    accessTokenClaims.put(JWT_CLAIMS_CLIENT_ID, generateOidcTokenDto.getClientId());
    accessTokenClaims.put(JWT_CLAIMS_JTI, RandomStringUtils.randomAlphanumeric(32));
    accessTokenClaims.put(JWT_CLAIMS_RFT_ID, getRftId(refreshToken));
    accessTokenClaims.put(
        JWT_CLAIMS_EXP, generateOidcTokenDto.getIat() + tokenConfig.getAccessTokenExpiry());
    accessTokenClaims.put(JWT_CLAIMS_SCOPE, String.join(" ", generateOidcTokenDto.getScope()));
    return tokenIssuer
        .generateAccessToken(accessTokenClaims, generateOidcTokenDto.getTenantId())
        .map(
            accessToken ->
                OidcTokenResponseDto.builder()
                    .accessToken(accessToken)
                    .refreshToken(refreshToken)
                    .tokenType(TOKEN_TYPE)
                    .expiresIn(tokenConfig.getAccessTokenExpiry())
                    .build());
  }

  private Boolean pkceTest(
      String codeVerifier, String codeChallenge, OidcCodeChallengeMethod codeChallengeMethod) {
    if (codeChallenge == null && codeChallengeMethod == null) return true;
    if (codeChallenge != null && codeChallengeMethod != null && codeVerifier == null)
      throw INVALID_REQUEST.getJsonCustomException("code_verifier is required");
    if (codeChallengeMethod.equals(OidcCodeChallengeMethod.PLAIN)) {
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

  private GenerateOidcTokenDto getGenerateOidcTokenDtoForAuthorizationCode(
      CodeSessionModel codeSessionModel, String tenantId, JsonObject userResponse) {
    return GenerateOidcTokenDto.builder()
        .clientId(codeSessionModel.getClient().getClientId())
        .userId(codeSessionModel.getUserId())
        .scope(codeSessionModel.getConsentedScopes())
        .userResponse(userResponse)
        .nonce(codeSessionModel.getNonce())
        .tenantId(tenantId)
        .iat(System.currentTimeMillis() / 1000)
        .build();
  }

  private GenerateOidcTokenDto getGenerateOidcTokenDtoForClientCredentials(
      String clientId, List<String> allowedScopes, String tenantId) {
    return GenerateOidcTokenDto.builder()
        .clientId(clientId)
        .userId(clientId)
        .scope(allowedScopes)
        .tenantId(tenantId)
        .iat(System.currentTimeMillis() / 1000)
        .build();
  }

  private GenerateOidcTokenDto getGenerateOidcTokenDtoForRefreshTokenFlow(
      String clientId, String userId, String tenantId, List<String> scopes) {
    return GenerateOidcTokenDto.builder()
        .clientId(clientId)
        .userId(userId)
        .tenantId(tenantId)
        .iat(System.currentTimeMillis() / 1000)
        .scope(scopes)
        .build();
  }

  private OidcRefreshTokenModel getOidcRefreshTokenModel(
      OidcTokenResponseDto tokenResponseDto, GenerateOidcTokenDto generateOidcTokenDto) {
    TenantConfig tenantConfig =
        registry.get(generateOidcTokenDto.getTenantId(), TenantConfig.class);
    TokenConfig tokenConfig = tenantConfig.getTokenConfig();
    return OidcRefreshTokenModel.builder()
        .tenantId(generateOidcTokenDto.getTenantId())
        .clientId(generateOidcTokenDto.getClientId())
        .userId(generateOidcTokenDto.getUserId())
        .refreshToken(tokenResponseDto.getRefreshToken())
        .refreshTokenExp(generateOidcTokenDto.getIat() + tokenConfig.getRefreshTokenExpiry())
        .scope(generateOidcTokenDto.getScope())
        .deviceName(generateOidcTokenDto.getDeviceName())
        .ip(generateOidcTokenDto.getIp())
        .build();
  }

  private Map<String, String> getUserFilters(String userId) {
    Map<String, String> filters = new HashMap<>();
    filters.put("userId", userId);
    return filters;
  }
}
