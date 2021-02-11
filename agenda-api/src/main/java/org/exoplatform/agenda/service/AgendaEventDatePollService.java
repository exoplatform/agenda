package org.exoplatform.agenda.service;

import java.time.ZoneId;
import java.util.List;
import java.util.Set;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
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
   * @param userIdentityId User technical identifier ({@link Identity#getId()})
   */
  List<EventDateOption> createEventPoll(long eventId, List<EventDateOption> dateOptions, long userIdentityId);

  /**
   * Update the list of Event Date options
   * 
   * @param eventId Technical identifier of {@link Event}
   * @param dateOptions {@link List} of {@link EventDateOption} corresponding to
   *          {@link Event}
   * @return {@link Set} of {@link AgendaEventModificationType} containing
   *         modifications made on event reminders
   */
  Set<AgendaEventModificationType> updateEventDateOptions(long eventId, List<EventDateOption> dateOptions);

  /**
   * Saves all votes of user on an event. The dateOptionVotes will provide only
   * accepted date options list. If dateOptionVotes is empty or null, this means
   * the user has dismissed all date options.
   * 
   * @param eventId Technical identifier of {@link Event}
   * @param acceptedDatePollIds {@link List} of accepted date poll options
   *          Technical identifier
   * @param identityId user {@link Identity} technical identifier
   * @throws ObjectNotFoundException when event with given id is not found
   * @throws IllegalAccessException when user can't vote on event
   */
  void saveEventVotes(long eventId, List<Long> acceptedDatePollIds, long identityId) throws ObjectNotFoundException,
                                                                                     IllegalAccessException;

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
