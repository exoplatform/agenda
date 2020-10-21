package org.exoplatform.agenda.model;

import lombok.*;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventSearchResult implements Serializable {

  private static final long           serialVersionUID = 8141201555219593461L;

  private long         id;

  private long         ownerId;

  private List<String> excerpts;

  private String       summary;

  private String       description;

  private String       start;

  private String       end;

  private String       location;

  private Boolean      isRecurrent;

  private List<String> attendees;

}
