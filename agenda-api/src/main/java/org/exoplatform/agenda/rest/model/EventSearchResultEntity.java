package org.exoplatform.agenda.rest.model;

import java.util.List;

import org.exoplatform.agenda.constant.EventAvailability;
import org.exoplatform.agenda.constant.EventStatus;
import org.exoplatform.agenda.model.EventConference;
import org.exoplatform.agenda.model.EventPermission;
import org.exoplatform.social.rest.entity.IdentityEntity;

import lombok.*;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EventSearchResultEntity extends EventEntity {

  private static final long serialVersionUID = 5124367759439151153L;

  public EventSearchResultEntity(final long id,
                                 final EventEntity parent,
                                 final String remoteId,
                                 final long remoteProviderId,
                                 final String remoteProviderName,
                                 final CalendarEntity calendar,
                                 final IdentityEntity creator,
                                 final String created,
                                 final String updated,
                                 final String summary,
                                 final String description,
                                 final String activityId,
                                 final String location,
                                 final String color,
                                 final String timeZoneId,
                                 final String start,
                                 final String end,
                                 final boolean allDay,
                                 final EventAvailability availability,
                                 final EventStatus status,
                                 final EventRecurrenceEntity recurrence,
                                 final EventOccurrenceEntity occurrence,
                                 final EventPermission acl,
                                 final List<EventAttendeeEntity> attendees,
                                 final List<EventConference> conferences,
                                 final List<EventReminderEntity> reminders,
                                 final boolean allowAttendeeToUpdate,
                                 final boolean allowAttendeeToInvite,
                                 final boolean sendInvitation,
                                 List<String> excerpts) {
    super(id,
          parent,
          remoteId,
          remoteProviderId,
          remoteProviderName,
          activityId,
          calendar,
          creator,
          created,
          updated,
          summary,
          description,
          location,
          color,
          timeZoneId,
          start,
          end,
          allDay,
          availability,
          status,
          recurrence,
          occurrence,
          acl,
          null,
          attendees,
          conferences,
          reminders,
          allowAttendeeToUpdate,
          allowAttendeeToInvite,
          sendInvitation);
    this.excerpts = excerpts;
  }

  private List<String> excerpts;

}
