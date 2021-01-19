package org.exoplatform.agenda.rest.model;

import java.io.Serializable;
import java.util.List;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventDateOptionEntity implements Serializable, Cloneable {

  private static final long serialVersionUID = -8336156948034042032L;

  private long              id;

  private long              eventId;

  private String            start;

  private String            end;

  private boolean           allDay;

  private boolean           selected;

  private List<Long>        voters;

  @Override
  protected EventDateOptionEntity clone() { // NOSONAR
    return new EventDateOptionEntity(id, eventId, start, end, allDay, selected, voters);
  }
}
