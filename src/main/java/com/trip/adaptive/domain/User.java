package com.trip.adaptive.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "users")
public class User {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(nullable = false)
  private String password;

  private String phone;

  public User() {}

  public User(String name, String email, String phone) {
    this.name = name;
    this.email = email;
    this.phone = phone;
  }

  public User(String name, String email, String password, String phone) {
    this.name = name;
    this.email = email;
    this.password = password;
    this.phone = phone;
  }

  public String getPassword() {
    return password;
  }

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String v) {
    name = v;
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String v) {
    email = v;
  }

  public String getPhone() {
    return phone;
  }

  public void setPhone(String v) {
    phone = v;
  }
}
