package com.example.darby.dto;

import java.util.List;

public class CrabTeam {
  public Integer id;
  public String name;
  public Boolean hidden;
  public Integer manager_id;
  public List<Mate> activeMembers;
  public Mate manager;

  public static class Mate {
    public Integer id;
    public String role;
    public String direction;
    public Employee employee;
  }

  public static class Employee {
    public String login;
    public String fullname;
    public String email;
    public String slack;
  }
}
