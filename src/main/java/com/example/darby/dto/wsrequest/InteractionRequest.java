package com.example.darby.dto.wsrequest;

import static com.example.darby.dto.wsrequest.WsRequestType.INTERACTION;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;

@JsonTypeName(value = "interactive")
public class InteractionRequest extends CommonWsRequest {
  public static final WsRequestType TYPE = INTERACTION;

  @JsonProperty("envelope_id")
  public String envelopeId;
  @JsonProperty("payload")
  public Payload payload;
  @JsonProperty("accepts_response_payload")
  public Boolean acceptsResponsePayload;
  @JsonProperty("retry_attempt")
  public Integer retryAttempt;
  @JsonProperty("retry_reason")
  public Integer retryReason;

  @Override
  public WsRequestType getType() {
    return TYPE;
  }
}
