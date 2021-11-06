package com.example.darby.dto.wsrequest;

import static com.example.darby.dto.wsrequest.WsRequestType.API_EVENT;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName(value = "events_api")
public class ApiEventRequest extends CommonWsRequest {
  public static final WsRequestType TYPE = API_EVENT;

  @JsonProperty("envelope_id")
  public String envelopeId;
  @JsonProperty("accepts_response_payload")
  public Boolean acceptsResponsePayload;
  @JsonProperty("payload")
  public Payload payload;
  @JsonProperty("retry_attempt")
  public Integer retryAttempt;
  @JsonProperty("retry_reason")
  public String retryReason;

  @Override
  public WsRequestType getType() {
    return TYPE;
  }
}
