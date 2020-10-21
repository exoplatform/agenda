package org.exoplatform.agenda.model;

import java.util.List;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EventSearchResult extends Event {

  private List<String> excerpts;

}
