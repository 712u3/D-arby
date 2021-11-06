package com.example.darby.dto.wsrequest;

import static com.example.darby.dto.wsrequest.WsRequestType.API_EVENT;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

//TODO decrease boilerplate
//TODO fix for fail in compile on unknown value
@JsonTypeName(value = "hello")
public class HelloRequest extends CommonWsRequest {
  public static final WsRequestType TYPE = API_EVENT;

  @JsonProperty("num_connections")
  public Integer numConnections;
  @JsonProperty("debug_info")
  public DebugInfo debugInfo;
  @JsonProperty("connection_info")
  public ConnectionInfo connectionInfo;

  @Override
  public WsRequestType getType() {
    return TYPE;
  }

  public static class DebugInfo {
    @JsonProperty("host")
    public String host;
    @JsonProperty("build_number")
    public Integer buildNumber;
    @JsonProperty("approximate_connection_time")
    public Integer approximateConnectionTime;
  }

  public static class ConnectionInfo {
    @JsonProperty("app_id")
    public String appId;
  }
}
