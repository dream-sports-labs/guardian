package com.dreamsportslabs.guardian.service.impl.idproviders;

import static com.dreamsportslabs.guardian.constant.Constants.DELIMITER_COMMA;
import static com.dreamsportslabs.guardian.constant.Constants.FACEBOOK_FIELDS_EMAIL;
import static com.dreamsportslabs.guardian.constant.Constants.FACEBOOK_FIELDS_FIRST_NAME;
import static com.dreamsportslabs.guardian.constant.Constants.FACEBOOK_FIELDS_FULL_NAME;
import static com.dreamsportslabs.guardian.constant.Constants.FACEBOOK_FIELDS_LAST_NAME;
import static com.dreamsportslabs.guardian.constant.Constants.FACEBOOK_FIELDS_MIDDLE_NAME;
import static com.dreamsportslabs.guardian.constant.Constants.FACEBOOK_FIELDS_PICTURE;
import static com.dreamsportslabs.guardian.constant.Constants.FACEBOOK_FIELDS_USER_ID;
import static com.dreamsportslabs.guardian.constant.Constants.FACEBOOK_GRAPHQL_HOST;
import static com.dreamsportslabs.guardian.constant.Constants.FACEBOOK_ME_QUERY_FILTER_ACCESS_TOKEN;
import static com.dreamsportslabs.guardian.constant.Constants.FACEBOOK_ME_QUERY_FILTER_APP_SECRET_PROOF;
import static com.dreamsportslabs.guardian.constant.Constants.FACEBOOK_ME_QUERY_FILTER_FIELDS;
import static com.dreamsportslabs.guardian.constant.Constants.FACEBOOK_ME_QUERY_PATH;
import static com.dreamsportslabs.guardian.exception.ErrorEnum.INTERNAL_SERVER_ERROR;
import static com.dreamsportslabs.guardian.exception.ErrorEnum.INVALID_REQUEST;

import com.dreamsportslabs.guardian.config.tenant.FbConfig;
import com.dreamsportslabs.guardian.injection.GuiceInjector;
import com.dreamsportslabs.guardian.service.IdProvider;
import com.google.common.hash.Hashing;
import io.reactivex.rxjava3.core.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.nio.charset.StandardCharsets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class FacebookIdProvider implements IdProvider {
  private final WebClient webClient;
  private final String appSecret;
  private final String fields = buildFacebookFieldList();

  public FacebookIdProvider(FbConfig fbConfigDto) {
    this.webClient = GuiceInjector.getGuiceInjector().getInstance(WebClient.class);
    this.appSecret = fbConfigDto.getAppSecret();
  }

  @Override
  public Single<JsonObject> getUserIdentity(String accessToken) {
    String appSecret =
        Hashing.hmacSha256(this.appSecret.getBytes())
            .hashString(accessToken, StandardCharsets.UTF_8)
            .toString();
    return webClient
        .get(443, FACEBOOK_GRAPHQL_HOST, FACEBOOK_ME_QUERY_PATH)
        .ssl(true)
        .addQueryParam(FACEBOOK_ME_QUERY_FILTER_ACCESS_TOKEN, accessToken)
        .addQueryParam(FACEBOOK_ME_QUERY_FILTER_APP_SECRET_PROOF, appSecret)
        .addQueryParam(FACEBOOK_ME_QUERY_FILTER_FIELDS, this.fields)
        .rxSend()
        .map(
            res -> {
              if (res.statusCode() == 200) {
                JsonObject jsonBody = res.bodyAsJsonObject();
                if (StringUtils.isNotBlank(jsonBody.getString(FACEBOOK_FIELDS_EMAIL))) {
                  return jsonBody;
                } else {
                  throw INVALID_REQUEST.getCustomException("Email unavailable");
                }
              } else if (res.statusCode() == 400) {
                throw INVALID_REQUEST.getCustomException("Invalid access token");
              } else {
                throw INTERNAL_SERVER_ERROR.getException();
              }
            });
  }

  public String buildFacebookFieldList() {
    return FACEBOOK_FIELDS_USER_ID
        + DELIMITER_COMMA
        + FACEBOOK_FIELDS_FULL_NAME
        + DELIMITER_COMMA
        + FACEBOOK_FIELDS_FIRST_NAME
        + DELIMITER_COMMA
        + FACEBOOK_FIELDS_MIDDLE_NAME
        + DELIMITER_COMMA
        + FACEBOOK_FIELDS_LAST_NAME
        + DELIMITER_COMMA
        + FACEBOOK_FIELDS_EMAIL
        + DELIMITER_COMMA
        + FACEBOOK_FIELDS_PICTURE;
  }
}
