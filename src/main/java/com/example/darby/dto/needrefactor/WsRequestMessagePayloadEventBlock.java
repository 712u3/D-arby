package com.example.darby.dto.needrefactor;

import java.util.List;

public class WsRequestMessagePayloadEventBlock {
  public String type;
  public String block_id;
  public List<Element> elements;

  public static class Element {
    public String type;
    public String text;
    public String user_id;
  }
}
