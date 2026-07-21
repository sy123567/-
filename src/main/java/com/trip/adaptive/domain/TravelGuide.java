package com.trip.adaptive.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;

@Entity
public class TravelGuide {
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  private User author;

  private String title;
  private String city;
  private int days;
  private String theme;
  private BigDecimal price;
  private String cover;
  private String description;

  @ElementCollection private List<String> tags = new ArrayList<>();

  private double rating;
  private int reviews;
  private int saves;
  private LocalDateTime createdAt;

  @ElementCollection private Set<Long> savedBy = new HashSet<>();

  public TravelGuide() {
    createdAt = LocalDateTime.now();
  }

  public Long getId() {
    return id;
  }

  public User getAuthor() {
    return author;
  }

  public void setAuthor(User v) {
    author = v;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String v) {
    title = v;
  }

  public String getCity() {
    return city;
  }

  public void setCity(String v) {
    city = v;
  }

  public int getDays() {
    return days;
  }

  public void setDays(int v) {
    days = v;
  }

  public String getTheme() {
    return theme;
  }

  public void setTheme(String v) {
    theme = v;
  }

  public BigDecimal getPrice() {
    return price;
  }

  public void setPrice(BigDecimal v) {
    price = v;
  }

  public String getCover() {
    return cover;
  }

  public void setCover(String v) {
    cover = v;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String v) {
    description = v;
  }

  public List<String> getTags() {
    return tags;
  }

  public void setTags(List<String> v) {
    tags = v;
  }

  public double getRating() {
    return rating;
  }

  public void setRating(double v) {
    rating = v;
  }

  public int getReviews() {
    return reviews;
  }

  public void setReviews(int v) {
    reviews = v;
  }

  public int getSaves() {
    return saves;
  }

  public void setSaves(int v) {
    saves = v;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public Set<Long> getSavedBy() {
    return savedBy;
  }

  public void setSavedBy(Set<Long> v) {
    savedBy = v;
  }
}
