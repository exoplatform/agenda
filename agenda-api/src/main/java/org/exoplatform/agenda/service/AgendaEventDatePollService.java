package org.exoplatform.agenda.service;

import java.time.ZoneId;
import java.util.List;

import org.exoplatform.agenda.model.Event;
import org.exoplatform.agenda.model.EventDateOption;
import org.exoplatform.commons.exception.ObjectNotFoundException;
import org.exoplatform.social.core.identity.model.Identity;

public interface AgendaEventDatePollService {

  /**
   * @param eventId Technical identifier of {@link Event}
   * @param userTimeZone {@link ZoneId} User time zone used to transform event
   *          options dates
   * @return {@link List} of {@link EventDateOption} of corresponding event
   */
  List<EventDateOption> getEventDateOptions(long eventId, ZoneId userTimeZone);

  /**
   * Retrieves {@link EventDateOption} identified by its Technical identifier
   * 
   * @param dateOptionId Technical identifier of {@link EventDateOption}
   * @param userTimeZone {@link ZoneId} User time zone used to transform event
   *          options dates
   * @return {@link EventDateOption} if found, else null
   */
  EventDateOption getEventDateOption(long dateOptionId, ZoneId userTimeZone);

  /**
   * Creates a list of date options for a given event
   * 
   * @param eventId Technical identifier of {@link Event}
   * @param dateOptions {@link List} of {@link EventDateOption} corresponding to
   *          {@link Event}
   * @return {@link List} of created {@link EventDateOption}
   */
  List<EventDateOption> createEventPoll(long eventId, List<EventDateOption> dateOptions);

  /**
   * Update the list of Event Date options
   * 
   * @param eventId Technical identifier of {@link Event}
   * @param dateOptions {@link List} of {@link EventDateOption} corresponding to
   *          {@link Event}
   * @return {@link List} of created {@link EventDateOption}
   */
  List<EventDateOption> updateEventDateOptions(long eventId, List<EventDateOption> dateOptions);

  /**
   * Add an event vote on an event for a participant.
   * 
   * @param dateOptionId Technical identifier of {@link EventDateOption}
   * @param identityId Technical identifier of {@link Identity} of type user
   * @throws ObjectNotFoundException when {@link EventDateOption} is not found
   *           using identifier
   * @throws IllegalAccessException when user is not participant of the event
   */
  void voteDateOption(long dateOptionId, long identityId) throws ObjectNotFoundException, IllegalAccessException;

  /**
   * Dismisses an event vote on an event for a participant.
   * 
   * @param dateOptionId Technical identifier of {@link EventDateOption}
   * @param identityId Technical identifier of {@link Identity} of type user
   * @throws ObjectNotFoundException when {@link EventDateOption} is not found
   *           using identifier
   */
  void dismissDateOption(long dateOptionId, long identityId) throws ObjectNotFoundException;

  /**
   * Select Date Option used to create event
   * 
   * @param dateOptionId Technical identifier of {@link EventDateOption}
   * @throws ObjectNotFoundException when {@link EventDateOption} is not found
   *           using identifier
   */
  void selectEventDateOption(long dateOptionId) throws ObjectNotFoundException;

}
