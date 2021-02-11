package org.exoplatform.agenda.service;

import java.util.*;

import org.apache.commons.lang.StringUtils;

import org.exoplatform.agenda.constant.AgendaEventModificationType;
import org.exoplatform.agenda.model.EventConference;
import org.exoplatform.agenda.storage.AgendaEventConferenceStorage;
import org.exoplatform.agenda.util.Utils;
import org.exoplatform.commons.api.settings.SettingService;
import org.exoplatform.commons.api.settings.SettingValue;
import org.exoplatform.commons.api.settings.data.Context;
import org.exoplatform.commons.api.settings.data.Scope;
import org.exoplatform.container.xml.InitParams;
import org.exoplatform.services.listener.ListenerService;

public class AgendaEventConferenceServiceImpl implements AgendaEventConferenceService {

  private static final String          WEB_CONFERENCING_ENABLED_TYPE_PARAM = "webConferencing.default";

  private static final String          AGENDA_WEB_CONFERENCING_KEY         = "AgendaWebConference";

  private static final Scope           AGENDA_SCOPE                        = Scope.APPLICATION.id("Agenda");

  private static final Context         AGENDA_CONTEXT                      = Context.GLOBAL.id("Agenda");

  private AgendaEventConferenceStorage conferenceStorage;

  private SettingService               settingService;

  private ListenerService              listenerService;

  private List<String>                 defaultEnabledWebConferences;

  public AgendaEventConferenceServiceImpl(AgendaEventConferenceStorage conferenceStorage,
                                          ListenerService listenerService,
                                          SettingService settingService,
                                          InitParams params) {
    this.conferenceStorage = conferenceStorage;
    this.listenerService = listenerService;
    this.settingService = settingService;

    if (params != null && params.containsKey(WEB_CONFERENCING_ENABLED_TYPE_PARAM)) {
      this.defaultEnabledWebConferences = Arrays.asList(params.getValueParam(WEB_CONFERENCING_ENABLED_TYPE_PARAM).getValue());
    }
  }

  @Override
  public Set<AgendaEventModificationType> saveEventConferences(long eventId, List<EventConference> conferences) {
    List<EventConference> savedConferences = getEventConferences(eventId);
    List<EventConference> newConferences = conferences == null ? Collections.emptyList() : conferences;
    List<EventConference> conferencesToDelete = new ArrayList<>(savedConferences);
    conferencesToDelete.removeAll(newConferences);
    List<EventConference> conferencesToCreate = new ArrayList<>(newConferences);
    conferencesToCreate.removeAll(savedConferences);

    // Delete conferences
    for (EventConference eventConference : conferencesToDelete) {
      conferenceStorage.removeEventConference(eventConference.getId());
    }

    // Create new conferences
    for (EventConference eventConference : conferencesToCreate) {
      eventConference.setEventId(eventId);
      conferenceStorage.saveEventConference(eventConference);
    }

    Set<AgendaEventModificationType> conferenceModificationTypes = new HashSet<>();
    if (!conferencesToDelete.isEmpty()) {
      conferenceModificationTypes.add(AgendaEventModificationType.CONFERENCE_DELETED);
    }
    if (!conferencesToCreate.isEmpty()) {
      conferenceModificationTypes.add(AgendaEventModificationType.CONFERENCE_ADDED);
    }
    Utils.broadcastEvent(listenerService, "exo.agenda.event.conferences.saved", eventId, 0);
    return conferenceModificationTypes;
  }

  @Override
  public List<EventConference> getEventConferences(long eventId) {
    return conferenceStorage.getEventConferences(eventId);
  }

  @Override
  public void saveEnabledWebConferenceProviders(List<String> enabledProviders) {
    SettingValue<?> webConferencingSetting = SettingValue.create(StringUtils.join(enabledProviders, ","));
    this.settingService.set(AGENDA_CONTEXT, AGENDA_SCOPE, AGENDA_WEB_CONFERENCING_KEY, webConferencingSetting);
  }

  @Override
  public List<String> getEnabledWebConferenceProviders() {
    SettingValue<?> webConferencingSetting = this.settingService.get(AGENDA_CONTEXT, AGENDA_SCOPE, AGENDA_WEB_CONFERENCING_KEY);
    if (webConferencingSetting == null || webConferencingSetting.getValue() == null) {
      return defaultEnabledWebConferences;
    }
    return Arrays.asList(StringUtils.split(webConferencingSetting.getValue().toString(), ","));
  }

}
