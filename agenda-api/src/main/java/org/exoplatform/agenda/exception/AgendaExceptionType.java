package org.exoplatform.agenda.exception;

public enum AgendaExceptionType {
  EVENT_START_DATE_BEFORE_END_DATE("endDate", "agenda.endDateMustBeAfterStartDate"),
  CALENDAR_NOT_FOUND("calendar", "agenda.calendarNotFound"),
  WRONG_EVENT_ATTENDEE_ID("attendee", "agenda.wrongAttendeeId"),
  EVENT_START_DATE_MANDATORY("startDate", "agenda.startDateMandatory"),
  EVENT_END_DATE_MANDATORY("endDate", "agenda.endDateMandatory"),
  EVENT_RECURRENCE_FREQUENCY_MANDATORY("recurrenceFrequency", "agenda.recurrenceFrequencyMandatory"),
  EVENT_RECURRENCE_INTERVAL_MANDATORY("recurrenceInterval", "agenda.recurrenceIntervalMandatory");

  private String field;

  private String message;

  private AgendaExceptionType(String field, String message) {
    this.field = field;
    this.message = message;
  }

  public String getMessage() {
    return message;
  }

  public String getField() {
    return field;
  }
}
