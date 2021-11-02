package com.example.darby.dto;

import java.util.List;

public class WsRequestMessagePayload {
  public String type;
  public String token;
  public String team_id;
  public String api_app_id;
  public String event_id;
  public String event_time;
  public String event_context;
  public WsRequestMessagePayloadUser channel;
  public Boolean is_ext_shared_channel;
  public List<WsRequestMessagePayloadAuthorization> authorizations;
  public WsRequestMessagePayloadEvent event;
  public WsRequestMessagePayloadMessage message;
  public String action_ts;
  public WsRequestMessagePayloadTeam team;
  public WsRequestMessagePayloadUser user;
  public String is_enterprise_install;
  public String enterprise;
  public String callback_id;
  public String trigger_id;
  public String response_url;
  public String message_ts;
}
