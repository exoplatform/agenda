package org.exoplatform.agenda.service;

import java.util.List;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventConference;

public interface AgendaEventConferenceService {

  /**
   * Return the list of conferences of an event
   * 
   * @param eventId agenda {@link Event} identifier
   * @return {@link List} of {@link EventConference}
   */
  public List<EventConference> getEventConferences(long eventId);

  /**
   * Save conferences for an {@link Event}
   * 
   * @param eventId Technical identifier of {@link Event}
   * @param conferences {@link List} of {@link EventConference}
   */
  void saveEventConferences(long eventId, List<EventConference> conferences);

}
