package com.example.darby.wcrequest;

public class WsRequestMessage {
  public String envelope_id;
  public String type;
  public Boolean accepts_response_payload;
  public Integer retry_attempt;
  public String retry_reason;
  public WsRequestMessagePayload payload;
}
