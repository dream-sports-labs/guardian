package com.dreamsportslabs.guardian.rest;

import static com.dreamsportslabs.guardian.constant.Constants.TENANT_ID;

import com.dreamsportslabs.guardian.dto.request.IdpConnectRequestDto;
import com.dreamsportslabs.guardian.service.OidcService;
import com.google.inject.Inject;
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

@Slf4j
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
@Path("/v1/idp/connect")
public class IdpConnect {
  private final OidcService oidcService;

  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response> connect(
      @Context HttpHeaders headers, IdpConnectRequestDto requestDto) {
    requestDto.validate();
    return oidcService
        .connect(requestDto, headers.getRequestHeaders(), headers.getHeaderString(TENANT_ID))
        .map(responseDto -> Response.ok(responseDto).build())
        .toCompletionStage();
  }
}
