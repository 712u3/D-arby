package com.example.darby.dto;

public class Acknowledge {
  public String envelope_id;
//  public String payload;

  public Acknowledge(String envelope_id) {
    this.envelope_id = envelope_id;
  }
}
