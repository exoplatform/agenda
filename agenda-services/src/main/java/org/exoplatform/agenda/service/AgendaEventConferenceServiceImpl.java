package org.exoplatform.agenda.service;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.exoplatform.agenda.model.EventConference;
import org.exoplatform.agenda.storage.AgendaEventConferenceStorage;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.services.listener.ListenerService;

public class AgendaEventConferenceServiceImpl implements AgendaEventConferenceService {
  private AgendaEventConferenceStorage conferenceStorage;

  private ListenerService              listenerService;

  public AgendaEventConferenceServiceImpl(AgendaEventConferenceStorage conferenceStorage,
                                          ListenerService listenerService) {
    this.conferenceStorage = conferenceStorage;
    this.listenerService = listenerService;
  }

  @Override
  public void saveEventConferences(long eventId, List<EventConference> conferences) {
    List<EventConference> savedConferences = getEventConferences(eventId);
    List<EventConference> newConferences = conferences == null ? Collections.emptyList() : conferences;
    List<EventConference> conferencesToDelete =
                                              savedConferences.stream()
                                                              .filter(conference -> newConferences.stream()
                                                                                                  .noneMatch(newConference -> newConference.getId() == conference.getId()))
                                                              .collect(Collectors.toList());

    // Delete conferences
    for (EventConference eventConference : conferencesToDelete) {
      conferenceStorage.removeEventConference(eventConference.getId());
    }

    // Create new conferences
    for (EventConference eventConference : newConferences) {
      eventConference.setEventId(eventId);
      conferenceStorage.saveEventConference(eventConference);
    }

    Utils.broadcastEvent(listenerService, "exo.agenda.event.conferences.saved", eventId, 0);
  }

  @Override
  public List<EventConference> getEventConferences(long eventId) {
    return conferenceStorage.getEventConferences(eventId);
  }

}
