package org.exoplatform.agenda.model;

import java.io.Serializable;
import java.time.ZonedDateTime;
import java.util.List;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class EventDateOption implements Serializable, Cloneable {

  private static final long serialVersionUID = 2017882533660661259L;

  private long              id;

  private long              eventId;

  private ZonedDateTime     start;

  private ZonedDateTime     end;

  private boolean           allDay;

  private boolean           selected;

  private List<Long>        voters;

  @Override
  protected EventDateOption clone() { // NOSONAR
    return new EventDateOption(id, eventId, start, end, allDay, selected, voters);
  }
}
