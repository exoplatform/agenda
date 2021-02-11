package org.exoplatform.agenda.service;

import java.util.List;
import java.util.Set;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
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
   * @return {@link Set} of {@link AgendaEventModificationType} containing
   *         modifications made on event conferences
   */
  Set<AgendaEventModificationType> saveEventConferences(long eventId, List<EventConference> conferences);

  /**
   * @return {@link List} of enabled web conferencing providers
   */
  List<String> getEnabledWebConferenceProviders();

  /**
   * @param enabledProviders {@link List} of enabled web conferencing providers
   *          types
   */
  void saveEnabledWebConferenceProviders(List<String> enabledProviders);

}
