package org.exoplatform.agenda.rest.model;

import lombok.*;
import org.exoplatform.agenda.model.EventSearchResult;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventSearchResultEntity implements Serializable {

  private static final long           serialVersionUID = 6767002872252834666L;

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
}
