package com.example.darby.dto;

import java.util.List;

public class WsRequestMessagePayloadMessage {
  public String client_msg_id;
  public String type;
  public String text;
  public String user;
  public String ts;
  public String team;
  public List<WsRequestMessagePayloadEventBlock> blocks;
}
