package com.example.darby.dto.wsrequest;

import java.util.HashMap;
import java.util.Map;

public enum WsRequestType {
  HELLO("hello"),
  API_EVENT("events_api"),
  INTERACTION("interactive");

  private final String value;

  private static final Map<String, WsRequestType> lookup = new HashMap<>();
  static {
    for (WsRequestType d : WsRequestType.values()) {
      lookup.put(d.getValue(), d);
    }
  }

  WsRequestType(String value) {
    this.value = value;
  }

  public String getValue() {
    return value;
  }

  public static WsRequestType get(String value) {
    return lookup.get(value);
  }
}
