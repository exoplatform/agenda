import {toRFC3339, getDayNameFromDate, getMonthNumberFromDate, toDate, USER_TIMEZONE_ID} from './AgendaUtils.js';
import {deleteEventWebConferencing, saveEventWebConferencing} from './EventWebConferencingService.js';

export function getEvents(query, ownerIds, attendeeIdentityId, start, end, limit, responseTypes, expand) {
  if (typeof start === 'object') {
    start = toRFC3339(start);
  }

  let params = {
    query: query || '',
    start: start,
    timeZoneId: USER_TIMEZONE_ID,
  };

  if (ownerIds && ownerIds.length) {
    params.ownerIds = ownerIds;
  }

  if (end) {
    end = toRFC3339(end);
    params.end = end;
  }

  if (limit && limit > 0) {
    params.limit = limit;
  }

  if (expand) {
    params.expand = expand;
  }

  if (attendeeIdentityId) {
    params.attendeeIdentityId = attendeeIdentityId;
  }

  if (responseTypes) {
    params.responseTypes = responseTypes;
  }

  params = $.param(params, true);

  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error getting event list');
    }
  });
}

export function getEventById(eventId, expand) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}?expand=${expand || ''}&timeZoneId=${USER_TIMEZONE_ID}`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error getting event');
    }
  }).then((event) => {
    event.startDate = toDate(event.start);
    event.endDate = toDate(event.end);
    return event;
  });
}

export function getEventOccurrence(parentEventId, occurrenceId, expand) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/occurrence/${parentEventId}/${occurrenceId}?expand=${expand || ''}&timeZoneId=${USER_TIMEZONE_ID}`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error getting occurrence of event');
    }
  }).then((event) => {
    event.startDate = toDate(event.start);
    event.endDate = toDate(event.end);
    return event;
  });
}
export function getEventExceptionalOccurrences(eventId, expand) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}/exceptionalOccurrences?expand=${expand || ''}&timeZoneId=${USER_TIMEZONE_ID}`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error getting occurrence of event');
    }
  });
}

export function createEvent(event) {
  if (!event.hasOwnProperty('sendInvitation')) {
    event.sendInvitation = true;
  }
  event = formatEventToSave(event);

  const saveWebConferencePromises = getSaveAllWebConferencesPromises(event);

  return Promise.all(saveWebConferencePromises)
    .catch(e => {
      console.error('Error saving web conferencing, delete them', e);
    })
    .then(conferences => {
      event.conferences = conferences && conferences.filter(conference => !!conference);
      return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events`, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(event),
      });
    })
    .then((resp) => {
      if (resp && resp.ok) {
        return resp.json();
      } else {
        throw new Error('Error creating event');
      }
    });
}

export function updateEvent(event) {
  if (!event.hasOwnProperty('sendInvitation')) {
    event.sendInvitation = true;
  }
  event = formatEventToSave(event);

  const saveWebConferencePromises = getSaveAllWebConferencesPromises(event);

  return Promise.all(saveWebConferencePromises)
    .catch(e => {
      console.error('Error updating web conferencing, delete them', e);
    })
    .then(conferences => {
      event.conferences = conferences && conferences.filter(conference => !!conference);
      return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events`, {
        method: 'PUT',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify(event),
      });
    }).then((resp) => {
      if (resp && resp.ok) {
        return resp.json();
      } else {
        throw new Error('Error updating event');
      }
    });
}

export function updateEventFields(eventId, eventFields, updateAllOccurrences, sendInvitations) {
  eventFields = formatEventToSave(eventFields);

  const formData = new FormData();
  Object.keys(eventFields).forEach(fieldName => {
    formData.append(fieldName, eventFields[fieldName]);
  });

  updateAllOccurrences = !!updateAllOccurrences;
  sendInvitations = !!sendInvitations;

  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}?updateAllOccurrences=${updateAllOccurrences}&sendInvitations=${sendInvitations}`, {
    method: 'PATCH',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams(formData).toString(),
  }).then((resp) => {
    if (!resp || !resp.ok) {
      throw new Error('Error patching event');
    }
  });
}

export function sendEventResponse(eventId, occurrenceId, response) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}/response/send?response=${response}&occurrenceId=${occurrenceId || ''}`, {
    method: 'GET',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
  }).then((resp) => {
    if (!resp || !resp.ok) {
      throw new Error('Error sending event response');
    }
  });
}

export function getUserReminderSettings() {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/settings`, {
    method: 'GET',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error retrieving reminders settings');
    }
  });
}

export function saveUserReminderSettings(reminders) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/settings`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(reminders),
  }).then((resp) => {
    if (!resp || !resp.ok) {
      throw new Error('Error sending default reminders settings');
    }
  });
}

export function saveEventReminders(eventId, occurrenceId, reminders, upcoming) {
  const remindersToSave = reminders && JSON.parse(JSON.stringify(reminders)) || [];
  remindersToSave.forEach(reminder => delete reminder.datetime);

  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}/reminders?occurrenceId=${occurrenceId ||''}&upcoming=${upcoming || false}`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(remindersToSave),
  }).then((resp) => {
    if (!resp || !resp.ok) {
      throw new Error('Error sending event reminders');
    }
  });
}

export function deleteEvent(eventId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}`, {
    method: 'DELETE',
    credentials: 'include',
  })
    .then((resp) => {
      if (resp && resp.ok) {
        return resp.json();
      } else {
        throw new Error('Error deleting event');
      }
    })
    .then(event => {
      if (event) {
        const deleteAllWebConferencesPromises = getDeleteAllWebConferencesPromises(event);
        return Promise.all(deleteAllWebConferencesPromises);
      }
    });
}

export function voteEventDate(eventId, dateOptionId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}/dateOption/${dateOptionId}/vote`, {
    method: 'POST',
    credentials: 'include',
  })
    .then((resp) => {
      if (!resp || !resp.ok) {
        throw new Error('Error voting on event');
      }
    });
}

export function selectEventDate(eventId, dateOptionId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}/dateOption/${dateOptionId}/select`, {
    method: 'POST',
    credentials: 'include',
  })
    .then((resp) => {
      if (!resp || !resp.ok) {
        throw new Error('Error selecting an event date');
      }
    });
}

export function dismissEventDate(eventId, dateOptionId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}/dateOption/${dateOptionId}/vote`, {
    method: 'DELETE',
    credentials: 'include',
  })
    .then((resp) => {
      if (!resp || !resp.ok) {
        throw new Error('Error voting on event');
      }
    });
}

function formatRecurrenceObject(event) {
  if (event.recurrence) {
    const recurrence = event.recurrence;
    const startDate = toDate(event.start);
    if (recurrence.frequency === 'WEEKLY') {
      recurrence.byMonthDay = null;
      recurrence.byMonth = null;
      if (!recurrence.byDay || !recurrence.byDay.length) {
        recurrence.byDay = [getDayNameFromDate(startDate).substring(0,2).toUpperCase()];
      }
    } else if (recurrence.frequency === 'MONTHLY') {
      recurrence.byDay = null;
      recurrence.byMonth = null;
      const dayNumberInMonth = startDate.getDate();
      recurrence.byMonthDay = [dayNumberInMonth];
    } else if(recurrence.frequency === 'YEARLY') {
      recurrence.byDay = null;
      const dayNumberInMonth = startDate.getDate();
      recurrence.byMonthDay = [dayNumberInMonth];
      const monthNumberFromDate = getMonthNumberFromDate(startDate);
      recurrence.byMonth = [monthNumberFromDate];
    } else {
      recurrence.byMonthDay = null;
      recurrence.byMonth = null;
      recurrence.byDay = null;
    }
  }
}

function formatEventToSave(event) {
  event = Object.assign({}, event);
  if (event.start) {
    event.start = toRFC3339(event.start);
  }
  if (event.end) {
    event.end = toRFC3339(event.end);
  }

  event = JSON.parse(JSON.stringify(event));
  if (event.calendar) {
    formatEventCalendar(event);
  }
  if (event.parent) {
    formatEventParent(event);
  }
  if (event.attendees) {
    formatEventAttendees(event);
  }
  if (event.recurrence) {
    formatRecurrenceObject(event);
  }

  if (event.creator) {
    delete event.creator;
  }
  return event;
}

function formatEventCalendar(event) {
  event.calendar = JSON.parse(JSON.stringify(event.calendar));
  event.calendar.owner = event.calendar.owner && {
    id: event.calendar.owner.id,
    providerId: event.calendar.owner.providerId,
    remoteId: event.calendar.owner.remoteId,
  };
}

function formatEventParent(event) {
  if (event.parent) {
    event.parent = {
      id: event.parent.id,
    };
    event.recurrence = null;
  }
}

function formatEventAttendees(event) {
  if (event.attendees && event.attendees.length) {
    event.attendees.forEach(attendee => {
      attendee.identity = {
        id: attendee.identity.id || 0,
        providerId: attendee.identity.providerId,
        remoteId: attendee.identity.remoteId,
      };
    });
  }
}

function getDeleteAllWebConferencesPromises(event) {
  const deleteWebConferencePromises = [];
  if (event.conferences && event.conferences.length) {
    event.conferences.forEach(conference => {
      const deleteWebConferencePromise = deleteEventWebConferencing(event, conference);
      deleteWebConferencePromises.push(deleteWebConferencePromise);
    });
  }
  return deleteWebConferencePromises;
}

function getSaveAllWebConferencesPromises(event) {
  const saveWebConferencePromises = [];
  if (event.conferences && event.conferences.length) {
    event.conferences.forEach(conference => {
      const saveWebConferencePromise = saveEventWebConferencing(event, conference);
      saveWebConferencePromises.push(saveWebConferencePromise);
    });
  }
  return saveWebConferencePromises;
}
