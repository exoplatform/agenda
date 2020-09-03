package org.exoplatform.agenda.rest.model;

import java.io.Serializable;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventOccurrenceEntity implements Serializable {

  private static final long serialVersionUID = -6680864287258344039L;

  private String            id;

  private boolean           exceptional;

}
