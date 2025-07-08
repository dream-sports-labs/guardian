package com.dreamsportslabs.guardian.dto.response;

import java.util.List;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class V1ContactBlockedFlowsResponseDto {
  private String contact;
  private List<String> blockedFlows;
  private int totalCount;
}
