package org.exoplatform.agenda.exception;

public enum AgendaExceptionType {
  EVENT_START_DATE_BEFORE_END_DATE("endDate", "agenda.endDateMustBeAfterStartDate"),
  CALENDAR_NOT_FOUND("calendar", "agenda.calendarNotFound"),
  CALENDAR_OWNER_NOT_FOUND("calendarOwner", "agenda.calendarOwnerNotFound"),
  WRONG_EVENT_ATTENDEE_ID("attendee", "agenda.wrongAttendeeId"),
  EVENT_START_DATE_MANDATORY("startDate", "agenda.startDateMandatory"),
  EVENT_END_DATE_MANDATORY("endDate", "agenda.endDateMandatory"),
  EVENT_RECURRENCE_FREQUENCY_MANDATORY("recurrenceFrequency", "agenda.recurrenceFrequencyMandatory"),
  EVENT_RECURRENCE_INTERVAL_MANDATORY("recurrenceInterval", "agenda.recurrenceIntervalMandatory"),
  EVENT_ID_MANDATORY("eventId", "agenda.eventIdMandatory"),
  EVENT_MANDATORY("event", "agenda.eventMandatory"),
  REMINDER_DATE_CANT_COMPUTE("eventReminder", "agenda.eventReminderCantBeComputed"),
  ATTENDEE_IDENTITY_NOT_FOUND("eventAttendee", "agenda.eventAttendeeIdentityMandatory");

  private String field;

  private String message;

  private String completeMessage;

  private AgendaExceptionType(String field, String message) {
    this.field = field;
    this.message = message;
    this.completeMessage = field + ":" + message;
  }

  public String getMessage() {
    return message;
  }

  public String getField() {
    return field;
  }

  public String getCompleteMessage() {
    return completeMessage;
  }

  @Override
  public String toString() {
    return completeMessage;
  }
}
