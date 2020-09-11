package org.exoplatform.agenda.rest.model;

import java.io.Serializable;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventOccurrenceEntity implements Serializable, Cloneable {

  private static final long serialVersionUID = -6680864287258344039L;

  private String            id;

  private boolean           exceptional;

  @Override
  public EventOccurrenceEntity clone() {// NOSONAR
    return new EventOccurrenceEntity(id, exceptional);
  }
}
