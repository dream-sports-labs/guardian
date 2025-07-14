package com.dreamsportslabs.guardian.it;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.dreamsportslabs.guardian.dto.request.V1AdminLogoutRequestDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

class AdminLogoutIT {

  private static final String USER_ID = "test-user-123";

  @Test
  @DisplayName("Should validate request DTO")
  void testRequestDtoValidation() {
    // Arrange
    V1AdminLogoutRequestDto requestDto = new V1AdminLogoutRequestDto();
    // Don't set userId to test validation

    // Act & Assert
    try {
      requestDto.validate();
      // Should not reach here
      assert false : "Expected exception was not thrown";
    } catch (Exception e) {
      // Expected validation exception
      assertNotNull(e.getMessage());
    }
  }

  @Test
  @DisplayName("Should accept valid request DTO")
  void testValidRequestDto() {
    // Arrange
    V1AdminLogoutRequestDto requestDto = new V1AdminLogoutRequestDto();
    requestDto.setUserId(USER_ID);

    // Act & Assert - should not throw exception
    requestDto.validate();
  }
} 