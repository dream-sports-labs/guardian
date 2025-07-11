package com.dreamsportslabs.guardian.dao.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ContactFlowBlockModel {
  private String tenantId;
  private String contact;
  private String flowName;
  private String reason;
  private Long unblockedAt;
  private boolean isActive;
}
