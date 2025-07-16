package com.dreamsportslabs.guardian.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;

@Getter
@Builder
public class V1BlockUserFlowResponseDto {
  private String userIdentifier;
  private List<String> blockedFlows;

  @Default private String message = "Flows blocked successfully";
}
