package org.exoplatform.agenda.model;

import java.util.List;

public class EventSearchResult {

  private long         id;

  private List<String> excerpts;

  private String       summary;

  private String       description;

  private String       start;

  private String       end;

  private String       location;

  private Boolean      isRecurrent;

  private List<String> attendees;

  public long getId() {
    return id;
  }

  public void setId(long id) {
    this.id = id;
  }

  public List<String> getExcerpts() {
    return excerpts;
  }

  public void setExcerpts(List<String> excerpts) {
    this.excerpts = excerpts;
  }

  public String getSummary() {
    return summary;
  }

  public void setSummary(String summary) {
    this.summary = summary;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String getStart() {
    return start;
  }

  public void setStart(String start) {
    this.start = start;
  }

  public String getEnd() {
    return end;
  }

  public void setEnd(String end) {
    this.end = end;
  }

  public String getLocation() {
    return location;
  }

  public void setLocation(String location) {
    this.location = location;
  }

  public List<String> getAttendees() {
    return attendees;
  }

  public void setAttendees(List<String> attendees) {
    this.attendees = attendees;
  }

  public Boolean getRecurrent() {
    return isRecurrent;
  }

  public void setRecurrent(Boolean recurrent) {
    isRecurrent = recurrent;
  }
}
