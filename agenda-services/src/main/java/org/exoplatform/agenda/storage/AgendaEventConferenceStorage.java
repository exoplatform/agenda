package org.exoplatform.agenda.storage;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.exoplatform.agenda.dao.EventConferenceDAO;
import org.exoplatform.agenda.dao.EventDAO;
import org.exoplatform.agenda.entity.EventConferenceEntity;
import org.exoplatform.agenda.entity.EventEntity;
import org.exoplatform.agenda.model.EventConference;
import org.exoplatform.agenda.model.EventConferenceMatch;
import org.exoplatform.agenda.util.EntityMapper;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.ListenerService;

public class AgendaEventConferenceStorage {

  private EventDAO           eventDAO;

  private EventConferenceDAO eventConferenceDAO;

  private ListenerService    listenerService;

  public AgendaEventConferenceStorage(EventDAO eventDAO,
                                      EventConferenceDAO eventConferenceDAO,
                                      ListenerService listenerService) {
    this.eventDAO = eventDAO;
    this.eventConferenceDAO = eventConferenceDAO;
    this.listenerService = listenerService;
  }

  public List<EventConference> getEventConferences(long eventId) {
    List<EventConferenceEntity> eventConferenceEntities = eventConferenceDAO.getEventConferences(eventId);
    if (eventConferenceEntities == null) {
      return Collections.emptyList();
    }
    return eventConferenceEntities.stream().map(EntityMapper::fromEntity).collect(Collectors.toList());
  }

  /**
   * Lists the meeting links of a conference type containing a text, each with
   * the series of the events holding it. The text is matched literally: the
   * LIKE wildcards it holds, <code>%</code> and <code>_</code>, are escaped.
   *
   * @param type conference type, the web conferencing provider type
   * @param text the text the link contains
   * @param limit maximum number of links to return
   * @return the links found, possibly empty
   */
  public List<EventConferenceMatch> findConferencesContaining(String type, String text, int limit) {
    String pattern = "%" + escapeLikeWildcards(text) + "%";
    return eventConferenceDAO.findByTypeAndUrlLike(type, pattern, limit)
                             .stream()
                             .map(row -> new EventConferenceMatch((String) row[0], ((Number) row[1]).longValue()))
                             .toList();
  }

  /**
   * Escapes the LIKE wildcards of a text, and the escape character itself,
   * with <code>!</code>, the escape character the conference queries declare.
   *
   * @param text the text to match literally
   * @return the escaped text
   */
  static String escapeLikeWildcards(String text) {
    return text.replace("!", "!!").replace("%", "!%").replace("_", "!_");
  }

  public EventConference saveEventConference(EventConference eventConference) {
    long eventId = eventConference.getEventId();
    EventEntity eventEntity = eventDAO.find(eventId);
    if (eventEntity == null) {
      throw new IllegalStateException("Can't find event with id " + eventId);
    }
    EventConferenceEntity eventConferenceEntity = EntityMapper.toEntity(eventConference);
    eventConferenceEntity.setEvent(eventEntity);

    if (eventConference.getId() <= 0) {
      eventConferenceEntity.setId(null);
      eventConferenceEntity = eventConferenceDAO.create(eventConferenceEntity);

      Utils.broadcastEvent(listenerService, "exo.agenda.event.conference.created", eventId, eventConferenceEntity.getId());
    } else {
      eventConferenceEntity = eventConferenceDAO.update(eventConferenceEntity);

      Utils.broadcastEvent(listenerService, "exo.agenda.event.conference.updated", eventId, eventConferenceEntity.getId());
    }
    return EntityMapper.fromEntity(eventConferenceEntity);
  }

  public void removeEventConference(long conferenceId) {
    EventConferenceEntity eventConferenceEntity = eventConferenceDAO.find(conferenceId);
    if (eventConferenceEntity != null) {
      eventConferenceDAO.delete(eventConferenceEntity);
      Utils.broadcastEvent(listenerService,
                           "exo.agenda.event.conference.deleted",
                           eventConferenceEntity.getEvent().getId(),
                           eventConferenceEntity.getId());
    }
  }

}
