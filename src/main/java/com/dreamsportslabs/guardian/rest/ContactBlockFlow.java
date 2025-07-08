package com.dreamsportslabs.guardian.rest;

import static com.dreamsportslabs.guardian.constant.Constants.TENANT_ID;

import com.dreamsportslabs.guardian.dao.model.ContactFlowBlockModel;
import com.dreamsportslabs.guardian.dto.request.V1BlockContactFlowRequestDto;
import com.dreamsportslabs.guardian.dto.request.V1UnblockContactFlowRequestDto;
import com.dreamsportslabs.guardian.dto.response.V1BlockContactFlowResponseDto;
import com.dreamsportslabs.guardian.dto.response.V1ContactBlockedFlowsResponseDto;
import com.dreamsportslabs.guardian.dto.response.V1UnblockContactFlowResponseDto;
import com.dreamsportslabs.guardian.service.ContactFlowBlockService;
import com.google.inject.Inject;
import io.reactivex.rxjava3.core.Single;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.List;
import java.util.concurrent.CompletionStage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Path("/v1/contact")
@RequiredArgsConstructor(onConstructor = @__({@Inject}))
public class ContactBlockFlow {
  private final ContactFlowBlockService contactFlowBlockService;

  @POST
  @Path("/block")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response> blockContact(
      @HeaderParam(TENANT_ID) String tenantId, V1BlockContactFlowRequestDto requestDto) {

    requestDto.validate();

    return contactFlowBlockService
        .blockContactFlows(requestDto, tenantId)
        .andThen(
            Single.fromCallable(
                () ->
                    V1BlockContactFlowResponseDto.builder()
                        .contact(requestDto.getContact())
                        .blockedFlows(requestDto.getBlockFlows())
                        .build()))
        .map(Response::ok)
        .map(Response.ResponseBuilder::build)
        .toCompletionStage();
  }

  @POST
  @Path("/unblock")
  @Consumes(MediaType.APPLICATION_JSON)
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response> unblockContact(
      @HeaderParam(TENANT_ID) String tenantId, V1UnblockContactFlowRequestDto requestDto) {

    requestDto.validate();

    return contactFlowBlockService
        .unblockContactFlows(requestDto, tenantId)
        .andThen(
            Single.fromCallable(
                () ->
                    V1UnblockContactFlowResponseDto.builder()
                        .contact(requestDto.getContact())
                        .unblockedFlows(requestDto.getUnblockFlows())
                        .build()))
        .map(Response::ok)
        .map(Response.ResponseBuilder::build)
        .toCompletionStage();
  }

  @GET
  @Path("/{contactId}/blocked-flows")
  @Produces(MediaType.APPLICATION_JSON)
  public CompletionStage<Response> getBlockedFlows(
      @HeaderParam(TENANT_ID) String tenantId, @PathParam("contactId") String contactId) {

    return contactFlowBlockService
        .getActiveFlowsBlockedForContact(tenantId, contactId)
        .map(
            activeBlocks -> {
              List<String> blockedFlows =
                  activeBlocks.stream().map(ContactFlowBlockModel::getFlowName).toList();

              V1ContactBlockedFlowsResponseDto responseDto =
                  V1ContactBlockedFlowsResponseDto.builder()
                      .contact(contactId)
                      .blockedFlows(blockedFlows)
                      .totalCount(blockedFlows.size())
                      .build();

              return Response.ok(responseDto).build();
            })
        .toCompletionStage();
  }
}
