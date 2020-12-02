package org.exoplatform.agenda.model;

import java.io.ByteArrayInputStream;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import org.exoplatform.ws.frameworks.json.impl.*;

import lombok.*;

@Data
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class AgendaUserSettings implements Cloneable {

  private String                       agendaDefaultView;

  private String                       agendaWeekStartOn;

  private boolean                      showWorkingTime;

  private String                       workingTimeStart;

  private String                       workingTimeEnd;

  private String                       connectedRemoteProvider;

  private String                       connectedRemoteUserId;

  private String                       timeZoneId;

  private List<EventReminderParameter> reminders;

  private List<RemoteProvider>         remoteProviders;

  public AgendaUserSettings(String agendaDefaultView,
                            String agendaWeekStartOn,
                            boolean showWorkingTime,
                            String workingTimeStart,
                            String workingTimeEnd,
                            String connectedRemoteProvider,
                            String connectedRemoteUserId,
                            String timeZoneId) {
    this.agendaDefaultView = agendaDefaultView;
    this.agendaWeekStartOn = agendaWeekStartOn;
    this.showWorkingTime = showWorkingTime;
    this.workingTimeStart = workingTimeStart;
    this.workingTimeEnd = workingTimeEnd;
    this.connectedRemoteProvider = connectedRemoteProvider;
    this.connectedRemoteUserId = connectedRemoteUserId;
    this.timeZoneId = timeZoneId;
  }

  @Override
  public String toString() {
    try {
      return new JsonGeneratorImpl().createJsonObject(this).toString();
    } catch (JsonException e) {
      throw new IllegalStateException("Error parsing current global object to string");
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
    return new AgendaUserSettings(agendaDefaultView,
                                  agendaWeekStartOn,
                                  showWorkingTime,
                                  workingTimeStart,
                                  workingTimeEnd,
                                  connectedRemoteProvider,
                                  connectedRemoteUserId,
                                  timeZoneId);
  }

}
