package org.exoplatform.agenda.rest.model;

import org.exoplatform.agenda.model.EventSearchResult;

import java.util.List;

public class EventSearchResultEntity {

  private String         id;

  private List<String>   excerpts;

  private CalendarEntity calendar;

  private String         summary;

  private String         description;

  private String         start;

  public EventSearchResultEntity(EventSearchResult eventSearchResult) {
    this.setId(String.valueOf(eventSearchResult.getId()));
    this.summary = eventSearchResult.getSummary();
    this.description = eventSearchResult.getDescription();
    this.excerpts = eventSearchResult.getExcerpts();
    this.start = eventSearchResult.getStart();
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
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

  public CalendarEntity getCalendar() {
    return calendar;
  }

  public void setCalendar(CalendarEntity calendar) {
    this.calendar = calendar;
  }
}
