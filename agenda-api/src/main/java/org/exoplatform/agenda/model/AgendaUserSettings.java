package org.exoplatform.agenda.model;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

import java.util.List;

@Data
@EqualsAndHashCode
@NoArgsConstructor
public class AgendaUserSettings {

  private static final String          SEPARATOR = " ";

  private String                       agendaDefaultView;

  private String                       agendaWeekStartOn;

  private boolean                      showWorkingTime;

  private String                       timeZoneId;

  private List<EventReminderParameter> reminderParameters;

  public AgendaUserSettings(String agendaDefaultView,
                            String agendaWeekStartOn,
                            boolean showWorkingTime,
                            String timeZoneId,
                            List<EventReminderParameter> reminderParameters) {
    this.agendaDefaultView = agendaDefaultView;
    this.agendaWeekStartOn = agendaWeekStartOn;
    this.showWorkingTime = showWorkingTime;
    this.timeZoneId = timeZoneId;
    this.reminderParameters = reminderParameters;
  }

    @Override
    public String toString() {
        return "AgendaUserSettings{" +
                "agendaDefaultView='" + agendaDefaultView + '\'' +
                ", agendaWeekStartOn='" + agendaWeekStartOn + '\'' +
                ", timeZoneId='" + timeZoneId + '\'' +
                ", showWorkingTime=" + showWorkingTime +
                ", reminders=" + reminderParameters +
                '}';
    }

    public static AgendaUserSettings fromString(String value) {
    if (StringUtils.isBlank(value)) {
      return null;
    }
    String[] values = value.split(SEPARATOR);
    // String[] reminderParameters = values[3].split(SEPARATOR);
    AgendaUserSettings agendaUserSettings = new AgendaUserSettings();
    agendaUserSettings.setAgendaDefaultView(values[0]);
    agendaUserSettings.setAgendaWeekStartOn(values[1]);
    agendaUserSettings.setShowWorkingTime(Boolean.parseBoolean(values[2]));
    agendaUserSettings.setTimeZoneId(values[3]);
    agendaUserSettings.setReminderParameters(null);

    return agendaUserSettings;

  }
}
