package com.dreamsportslabs.guardian.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Builder.Default;
import lombok.Getter;

@Getter
@Builder
public class V1UnblockUserFlowResponseDto {
  private String userIdentifier;
  private List<String> unblockedFlows;

  @Default private String message = "Flows unblocked successfully";
}
