package com.dreamsportslabs.guardian.constant;

import lombok.Getter;

@Getter
public enum ResponseType {
  CODE("code"),
  TOKEN("token"),
  COOKIE("cookie");

  private final String responseType;

  ResponseType(String responseType) {
    this.responseType = responseType;
  }
}
