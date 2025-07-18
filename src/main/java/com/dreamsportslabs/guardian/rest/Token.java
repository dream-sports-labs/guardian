package com.dreamsportslabs.guardian.rest;

import static com.dreamsportslabs.guardian.constant.Constants.AUTHORIZATION;
import static com.dreamsportslabs.guardian.constant.Constants.TENANT_ID;
import static com.dreamsportslabs.guardian.exception.OidcErrorEnum.INVALID_CLIENT;

import com.dreamsportslabs.guardian.dto.request.TokenRequestDto;
import com.dreamsportslabs.guardian.service.OidcTokenService;
import com.google.inject.Inject;
import jakarta.ws.rs.BeanParam;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
@Path("/token")
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class Token {

  private final OidcTokenService oidcTokenService;

  @POST
  @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response> token(
      @BeanParam TokenRequestDto requestDto, @Context HttpHeaders headers) {
    String tenantId = headers.getHeaderString(TENANT_ID);
    String authorizationHeader = headers.getHeaderString(AUTHORIZATION);

    requestDto.validate();
    if (StringUtils.isBlank(authorizationHeader) && StringUtils.isBlank(requestDto.getClientId())) {
      throw INVALID_CLIENT.getException();
    }

    return oidcTokenService
        .getOidcTokens(requestDto, tenantId, authorizationHeader, headers.getRequestHeaders())
        .map(
            dto ->
                Response.ok(dto)
                    .header("Cache-Control", "no-store")
                    .header("Pragma", "no-cache")
                    .build())
        .toCompletionStage();
  }
}
