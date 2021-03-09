package org.exoplatform.agenda.model;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.ws.frameworks.json.impl.*;

import lombok.*;
import lombok.ToString.Exclude;

@Data
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class AgendaUserSettings implements Cloneable {

  private String                       agendaDefaultView       = null;

  private String                       agendaWeekStartOn       = null;

  private boolean                      showWorkingTime         = false;

  private String                       workingTimeStart        = null;

  private String                       workingTimeEnd          = null;

  private String                       connectedRemoteProvider = null;

  private String                       connectedRemoteUserId   = null;

  @Exclude
  @lombok.EqualsAndHashCode.Exclude
  private String                       cometdToken             = null;

  @Exclude
  @lombok.EqualsAndHashCode.Exclude
  private String                       cometdContextName       = null;

  private boolean                      automaticPushEvents     = true;

  private String                       timeZoneId              = null;

  private List<EventReminderParameter> reminders               = null;

  private List<RemoteProvider>         remoteProviders         = null;

  private List<String>                 webConferenceProviders  = null;

  public AgendaUserSettings(String cometdToken,
                            String agendaDefaultView,
                            String agendaWeekStartOn,
                            boolean showWorkingTime,
                            String workingTimeStart,
                            String workingTimeEnd,
                            String connectedRemoteProvider,
                            String connectedRemoteUserId,
                            boolean automaticPushEvents,
                            String timeZoneId) {
    this.cometdToken = cometdToken;
    this.agendaDefaultView = agendaDefaultView;
    this.agendaWeekStartOn = agendaWeekStartOn;
    this.showWorkingTime = showWorkingTime;
    this.workingTimeStart = workingTimeStart;
    this.workingTimeEnd = workingTimeEnd;
    this.connectedRemoteProvider = connectedRemoteProvider;
    this.connectedRemoteUserId = connectedRemoteUserId;
    this.automaticPushEvents = automaticPushEvents;
    this.timeZoneId = timeZoneId;
  }

  @Override
  public String toString() {
    try {
      return new JsonGeneratorImpl().createJsonObject(this).toString();
    } catch (JsonException e) {
      throw new IllegalStateException("Error parsing current global object to string", e);
    }
  }

  public static AgendaUserSettings fromString(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    try {
      JsonDefaultHandler jsonDefaultHandler = new JsonDefaultHandler();
      new JsonParserImpl().parse(new ByteArrayInputStream(value.getBytes()), jsonDefaultHandler);
      return ObjectBuilder.createObject(AgendaUserSettings.class, jsonDefaultHandler.getJsonObject());
    } catch (JsonException e) {
      throw new IllegalStateException("Error creating object from string : " + value, e);
    }
  }

  @Override
  public AgendaUserSettings clone() { // NOSONAR
    return new AgendaUserSettings(cometdToken,
                                  agendaDefaultView,
                                  agendaWeekStartOn,
                                  showWorkingTime,
                                  workingTimeStart,
                                  workingTimeEnd,
                                  connectedRemoteProvider,
                                  connectedRemoteUserId,
                                  automaticPushEvents,
                                  timeZoneId);
  }

}
