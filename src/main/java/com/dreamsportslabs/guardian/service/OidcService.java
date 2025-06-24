package com.dreamsportslabs.guardian.service;

import static com.dreamsportslabs.guardian.constant.Constants.*;
import static com.dreamsportslabs.guardian.exception.ErrorEnum.*;

import com.dreamsportslabs.guardian.config.tenant.OidcProviderConfig;
import com.dreamsportslabs.guardian.constant.Flow;
import com.dreamsportslabs.guardian.dao.OidcProviderDao;
import com.dreamsportslabs.guardian.dao.model.IdpCredentialsModel;
import com.dreamsportslabs.guardian.dto.Provider;
import com.dreamsportslabs.guardian.dto.UserDto;
import com.dreamsportslabs.guardian.dto.request.IdpConnectRequestDto;
import com.dreamsportslabs.guardian.dto.request.MetaInfo;
import com.dreamsportslabs.guardian.dto.response.IdpConnectResponseDto;
import com.dreamsportslabs.guardian.exception.ErrorEnum;
import com.google.inject.Inject;
import io.fusionauth.jwt.JWTDecoder;
import io.fusionauth.jwt.domain.JWT;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.core.MultiMap;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.WebClient;
import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.Response;
import java.util.HashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class OidcService {
  private final OidcProviderDao oidcProviderDao;
  private final UserService userService;
  private final AuthorizationService authorizationService;
  private final WebClient webClient;
  private final JWTDecoder jwtDecoder = JWT.getDecoder();

  public Single<IdpConnectResponseDto> connect(
      IdpConnectRequestDto requestDto, MultivaluedMap<String, String> headers, String tenantId) {
    return oidcProviderDao
        .getOidcProviderConfig(tenantId, requestDto.getIdProvider())
        .switchIfEmpty(Single.error(PROVIDER_NOT_FOUND.getException()))
        .flatMap(
            providerConfig -> {
              String userIdentifier = providerConfig.getUserIdentifier();
              String providerName = requestDto.getIdProvider();

              return getProviderTokens(requestDto, providerConfig)
                  .flatMap(
                      idpTokens -> {
                        validateIdToken(
                            idpTokens.getIdToken(), providerConfig, requestDto.getNonce());

                        Provider provider =
                            getProviderDtoFromTokens(idpTokens, providerName, userIdentifier);
                        UserDto userDto = getUserDtoFromTokens(idpTokens.getIdToken(), provider);

                        return checkUserIdentifierAndUserExists(
                                userIdentifier,
                                userDto,
                                requestDto.getIdProvider(),
                                requestDto.getFlow(),
                                headers,
                                providerConfig)
                            .flatMap(userJson -> loginUser(userJson, requestDto, headers, tenantId))
                            .map(response -> buildIdpConnectResponse(response, idpTokens));
                      });
            });
  }

  private Provider getProviderDtoFromTokens(
      IdpCredentialsModel idpTokens, String providerName, String userIdentifier) {
    try {
      JWT jwt = jwtDecoder.decode(idpTokens.getIdToken());
      Map<String, Object> claims = jwt.getAllClaims();
      Map<String, Object> credentials = new HashMap<>();
      credentials.put("access_token", idpTokens.getAccessToken());
      credentials.put("refresh_token", idpTokens.getRefreshToken());
      return Provider.builder()
          .name(providerName)
          .data(claims)
          .providerUserId(userIdentifier)
          .credentials(credentials)
          .build();
    } catch (Exception e) {
      throw new RuntimeException("Failed to decode JWT token", e);
    }
  }

  private UserDto getUserDtoFromTokens(String idToken, Provider providerDto) {
    try {
      JWT jwt = jwtDecoder.decode(idToken);
      Map<String, Object> claims = jwt.getAllClaims();
      UserDto.UserDtoBuilder userDtoBuilder = UserDto.builder();
      if (claims.containsKey(GIVEN_NAME))
        userDtoBuilder.firstName(claims.get(GIVEN_NAME).toString());
      if (claims.containsKey(FAMILY_NAME))
        userDtoBuilder.lastName(claims.get(FAMILY_NAME).toString());
      if (claims.containsKey(NAME)) userDtoBuilder.name(claims.get(NAME).toString());
      if (claims.containsKey(EMAIL)) userDtoBuilder.email(claims.get(EMAIL).toString());
      if (claims.containsKey(PHONE)) userDtoBuilder.phoneNumber(claims.get(PHONE).toString());
      userDtoBuilder.provider(providerDto);
      return userDtoBuilder.build();
    } catch (Exception e) {
      throw new RuntimeException("Failed to decode JWT token", e);
    }
  }

  private void validateIdToken(String idToken, OidcProviderConfig providerConfig, String nonce) {
    try {
      JWT jwt = jwtDecoder.decode(idToken);

      String issuer = jwt.getString("iss");
      if (issuer == null || !providerConfig.getIssuer().equals(issuer)) {
        throw INVALID_IDP_TOKEN.getException();
      }

      String audience = jwt.getString("aud");
      if (audience == null || !providerConfig.getClientId().equals(audience)) {
        throw INVALID_IDP_TOKEN.getException();
      }

      Long exp = jwt.getLong("exp");
      if (exp == null || exp < System.currentTimeMillis() / 1000) {
        throw INVALID_IDP_TOKEN.getCustomException("ID token has expired");
      }

      Long iat = jwt.getLong("iat");
      if (iat == null || iat > System.currentTimeMillis() / 1000) {
        throw INVALID_IDP_TOKEN.getCustomException("Invalid issued at time in ID token");
      }

      String subject = jwt.getString("sub");
      if (subject == null || subject.trim().isEmpty()) {
        throw INVALID_IDP_TOKEN.getCustomException("Missing subject in ID token");
      }

      if (nonce != null && !nonce.trim().isEmpty()) {
        String tokenNonce = jwt.getString("nonce");
        if (tokenNonce == null || !nonce.equals(tokenNonce)) {
          throw INVALID_IDP_TOKEN.getCustomException("Invalid nonce in ID token");
        }
      }

      log.info("ID token validation successful for provider: {}", providerConfig.getProviderName());

    } catch (Exception e) {
      if (e instanceof RuntimeException && e.getMessage().contains("Invalid")) {
        throw e;
      }
      throw INVALID_IDP_TOKEN.getCustomException("ID token validation failed: " + e.getMessage());
    }
  }

  private Single<IdpCredentialsModel> getProviderTokens(
      IdpConnectRequestDto requestDto, OidcProviderConfig providerConfig) {
    return exchangeCodeForTokens(requestDto, providerConfig)
        .onErrorResumeNext(
            err -> Single.error(INVALID_IDP_CODE.getCustomException(err.getMessage())));
  }

  private Single<JsonObject> checkUserIdentifierAndUserExists(
      String userIdentifier,
      UserDto userDto,
      String idProvider,
      Flow flow,
      MultivaluedMap<String, String> headers,
      OidcProviderConfig applicationConfig) {

    Map<String, String> queryParams = new HashMap<>();
    if (validateUserIdentifier(userIdentifier, queryParams, userDto, idProvider)) {
      throw ErrorEnum.INVALID_USER_IDENTIFIER.getException();
    }

    return userService
        .getUser(queryParams, headers, applicationConfig.getTenantId())
        .flatMap(
            user -> {
              boolean userExists = user.getString(USERID) != null;

              switch (flow) {
                case SIGNIN:
                  if (!userExists) {
                    return Single.error(USER_NOT_EXISTS.getCustomException("User does not exist"));
                  }
                  return Single.just(user);

                case SIGNUP:
                  if (userExists) {
                    return Single.error(USER_EXISTS.getCustomException("User already exists"));
                  }
                  return userService.createUser(userDto, headers, applicationConfig.getTenantId());

                case SIGNINUP:
                  if (userExists) {
                    return Single.just(user);
                  } else {
                    return userService.createUser(
                        userDto, headers, applicationConfig.getTenantId());
                  }

                default:
                  return Single.error(new IllegalArgumentException("Invalid flow type: " + flow));
              }
            });
  }

  private boolean validateUserIdentifier(
      String userIdentifier, Map<String, String> queryParams, UserDto userDto, String idProvider) {
    boolean isUserIdentifierExists;
    switch (userIdentifier) {
      case PHONE:
        isUserIdentifierExists = StringUtils.isNotBlank(userDto.getPhoneNumber());
        queryParams.put(PHONE, userDto.getPhoneNumber());
        break;
      case JWT_CLAIMS_SUB:
        isUserIdentifierExists = StringUtils.isNotBlank(userDto.getProvider().getProviderUserId());
        queryParams.put(JWT_CLAIMS_SUB, userDto.getProvider().getProviderUserId());
        queryParams.put(PROVIDER, idProvider);
        break;
      default:
        isUserIdentifierExists = StringUtils.isNotBlank(userDto.getEmail());
        queryParams.put(EMAIL, userDto.getEmail());
    }
    return !isUserIdentifierExists;
  }

  private Single<Response> loginUser(
      JsonObject userJson,
      IdpConnectRequestDto requestDto,
      MultivaluedMap<String, String> headers,
      String tenantId) {

    Boolean isNewUser = userJson.getBoolean("isNewUser", false);

    return loginUser(
        userJson.getString(USERID),
        requestDto.getResponseType(),
        requestDto.getMetaInfo(),
        tenantId,
        isNewUser);
  }

  private Single<Response> loginUser(
      String userId, String responseType, MetaInfo metaInfo, String tenantId, Boolean isNewUser) {

    JsonObject user = new JsonObject().put(USERID, userId).put("isNewUser", isNewUser);

    switch (responseType) {
      case TOKEN:
        return authorizationService
            .generate(user, TOKEN, metaInfo, tenantId)
            .map(
                responseBuilder -> {
                  Response response = responseBuilder.build();
                  JsonObject responseBody = (JsonObject) response.getEntity();
                  responseBody.put("isNewUser", isNewUser);
                  return Response.ok(responseBody).build();
                });
      case CODE:
        return authorizationService
            .generate(user, CODE, metaInfo, tenantId)
            .map(
                responseBuilder -> {
                  Response response = responseBuilder.build();
                  JsonObject responseBody = (JsonObject) response.getEntity();
                  responseBody.put("isNewUser", isNewUser);
                  return Response.ok(responseBody).build();
                });
      default:
        throw new IllegalArgumentException("Invalid response type: " + responseType);
    }
  }

  private IdpConnectResponseDto buildIdpConnectResponse(
      Response response, IdpCredentialsModel idpTokens) {
    JsonObject responseBody = (JsonObject) response.getEntity();

    return IdpConnectResponseDto.builder()
        .accessToken(responseBody.getString("accessToken"))
        .refreshToken(responseBody.getString("refreshToken"))
        .idToken(responseBody.getString("idToken"))
        .tokenType(responseBody.getString("tokenType"))
        .expiresIn(responseBody.getInteger("expiresIn"))
        .isNewUser(responseBody.getBoolean("isNewUser", false))
        .idpCredentials(idpTokens)
        .build();
  }

  public Single<IdpCredentialsModel> exchangeCodeForTokens(
      IdpConnectRequestDto requestDto, OidcProviderConfig providerConfig) {

    String tokenUrl = providerConfig.getTokenUrl();
    String clientId = providerConfig.getClientId();
    String clientSecret = providerConfig.getClientSecret();
    String redirectUri = providerConfig.getRedirectUri();
    Boolean isSslEnabled = providerConfig.getIsSslEnabled();

    MultiMap oidcTokenRequestBody = MultiMap.caseInsensitiveMultiMap();
    oidcTokenRequestBody.add("grant_type", "authorization_code");
    oidcTokenRequestBody.add("code", requestDto.getIdentifier());
    oidcTokenRequestBody.add("redirect_uri", redirectUri);
    if (StringUtils.isNotBlank(requestDto.getCodeVerifier())) {
      oidcTokenRequestBody.add("code_verifier", requestDto.getCodeVerifier());
    }

    HttpRequest<Buffer> httpRequest = webClient.postAbs(tokenUrl).ssl(isSslEnabled);

    oidcTokenRequestBody.add("client_id", clientId);
    oidcTokenRequestBody.add("client_secret", clientSecret);

    return httpRequest
        .rxSendForm(oidcTokenRequestBody)
        .map(
            res -> {
              if (res.statusCode() == 200) {
                JsonObject jsonBody = res.bodyAsJsonObject();
                if (StringUtils.isNotBlank(jsonBody.getString(OIDC_ID_TOKEN))) {
                  return IdpCredentialsModel.builder()
                      .accessToken(jsonBody.getString(OIDC_ACCESS_TOKEN))
                      .refreshToken(jsonBody.getString(OIDC_REFRESH_TOKEN))
                      .idToken(jsonBody.getString(OIDC_ID_TOKEN))
                      .build();
                } else {
                  throw INVALID_IDP_CODE.getCustomException("Missing ID token in response");
                }
              } else if (res.statusCode() >= 400 && res.statusCode() < 500) {
                String errorDescription = res.bodyAsJsonObject().getString("error_description");
                errorDescription =
                    StringUtils.isEmpty(errorDescription)
                        ? "Invalid OIDC authorization code"
                        : errorDescription;
                throw INVALID_IDP_CODE.getCustomException(errorDescription);
              } else {
                throw INTERNAL_SERVER_ERROR.getCustomException("Token exchange failed");
              }
            })
        .onErrorResumeNext(
            err -> Single.error(INVALID_IDP_CODE.getCustomException(err.getMessage())));
  }
}
