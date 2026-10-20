import {toRFC3339, getDayNameFromDate, getMonthNumberFromDate, toDate, USER_TIMEZONE_ID} from './AgendaUtils.js';
import {deleteEventWebConferencing, saveEventWebConferencing} from './EventWebConferencingService.js';

/**
 * Retrieves the events of a period.
 *
 * @param {String} query full text search term, may be null
 * @param {Array|boolean} ownerIds identity ids of the calendar owners to
 *          display, empty for every calendar the user can access
 * @param {Number} attendeeIdentityId identity id to filter on attendees
 * @param {String|Date} start beginning of the period
 * @param {String|Date} end end of the period, may be null
 * @param {Number} limit maximum number of events, 0 for no limit
 * @param {Array} responseTypes attendee responses to keep
 * @param {String} expand properties to expand
 * @param {Array} excludedCalendarIds technical ids of the calendars to leave
 *          out of the results, whatever the selected owners bring in: the
 *          personal calendars of a user all share one owner, so ownerIds
 *          alone cannot express a selection among them
 * @param {Array} calendarIds identifiers of the calendars other users shared
 *          with the user whose events are wanted too, may be empty (EXO-90357)
 * @param {boolean} includeSubscribedCalendars with an attendee, also reads
 *          the calendars the owners of this listing subscribed to (EXO-90373),
 *          whatever the response types: their events carry no attendee row
 * @returns {Promise} resolved with the event list
 */
export function getEvents(query, ownerIds, attendeeIdentityId, start, end, limit, responseTypes, expand, excludedCalendarIds, calendarIds, includeSubscribedCalendars) {
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

  if (excludedCalendarIds && excludedCalendarIds.length) {
    params.excludedCalendarIds = excludedCalendarIds;
  }

  // The calendars colleagues shared with the user (EXO-90357), asked for on
  // top of the owner selection: the server checks each against the user
  if (calendarIds && calendarIds.length) {
    params.calendarIds = calendarIds;
  }

  // Only sent when true: absent is the server's default, which leaves them out
  if (includeSubscribedCalendars) {
    params.includeSubscribedCalendars = true;
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
  }).then((data) => {
    let events = data && data.events || [];
    let deletedEventId = localStorage.getItem('agendaDeletedEvents');
    if (deletedEventId) {
      deletedEventId = Number(deletedEventId);
      events = events.filter(event => Number(event.id) !== deletedEventId && (!event.parent || Number(event.parent.id) !== deletedEventId));
      data.events = events;
    }
    return data;
  });
}

export function getEventById(eventId, expand, firstOccurrence) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}?expand=${expand || ''}&timeZoneId=${USER_TIMEZONE_ID}&firstOccurrence=${firstOccurrence || false}`, {
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

/**
 * The second half of a "this and upcoming" split: moves the dates the
 * organiser had customised from the displayed one on under the series that
 * continues the first one, so that the shortening of the first series does not
 * drop them with their answers. Called after the continuing series is created
 * and before the first series is shortened.
 *
 * @param {number} eventId the first series
 * @param {number} toEventId the continuing series
 * @param {string} from the occurrence id of the displayed date (RFC 3339)
 * @returns {Promise<Array<number>>} the identifiers of the rows moved and kept
 */
export function moveExceptionalOccurrences(eventId, toEventId, from) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}/exceptionalOccurrences/move?toEventId=${toEventId}&from=${encodeURIComponent(from)}`, {
    method: 'POST',
    credentials: 'include',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error moving the exceptional occurrences');
    }
  });
}

/**
 * Splits a recurring event at one of its dates, a "this and upcoming" change,
 * in one request: the series continuing it from that date is created with the
 * fields given, the dates customised from it on move under it, and the event
 * ends the day before. The participants are the series' own, with the change
 * given as a delta.
 *
 * @param {Number} seriesId the recurring event
 * @param {Object} event the continuing series as edited; its participants are
 *          the ones its conferences are synchronised with, and are not sent
 * @param {Object} delta the participants added and the participant keys
 *          removed (see AgendaUtils#attendeesDelta), null when nobody changed
 * @param {String} splitFrom the occurrence id of the date it starts on
 * @param {Boolean} sendInvitation whether the participants are notified
 * @param {Boolean} confirmClose whether the organiser confirmed closing the
 *          event while people wait for a seat: without it, such a split is
 *          refused with agenda.capacity.closeNeedsConfirmation
 * @param {Array} seriesFields the fields of the event the series keeps as its
 *          own, which are not sent: among them, start, end, allDay and
 *          recurrence. What is sent is written in the zone the series was
 *          read in, which the request names: the series keeps its own zone,
 *          and the server moves the one day a rule derived from the date
 *          names there onto the series' zone (see EventSplit#readZone)
 * @returns {Promise} the continuing series
 */
export function splitEvent(seriesId, event, delta, splitFrom, sendInvitation, confirmClose, seriesFields) {
  event = formatEventToSave(event);
  // Synchronised on a copy: the participants are not sent
  const saveWebConferencePromises = getSaveAllWebConferencesPromises(Object.assign({}, event));
  delete event.attendees;
  (seriesFields || []).forEach(field => delete event[field]);
  const addedAttendees = {attendees: JSON.parse(JSON.stringify(delta && delta.added || []))};
  formatEventAttendees(addedAttendees);

  return Promise.all(saveWebConferencePromises)
    .catch(e => {
      console.error('Error saving web conferencing, delete them', e);
    })
    .then(conferences => {
      event.conferences = conferences && conferences.filter(conference => !!conference);
      return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${seriesId}/split?timeZoneId=${USER_TIMEZONE_ID}&confirmClose=${!!confirmClose}`, {
        method: 'POST',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/json'
        },
        body: JSON.stringify({
          event,
          addedAttendees: addedAttendees.attendees,
          removedAttendees: (delta && delta.removedParticipantKeys || []).map(toRemovedAttendee),
          splitFrom,
          sendInvitation: !!sendInvitation,
        }),
      });
    })
    .then((resp) => {
      if (resp && resp.ok) {
        return resp.json();
      } else {
        return toSaveError(resp, 'Error splitting event');
      }
    });
}

/**
 * @param {String} participantKey a participant key (see
 *          AgendaUtils#participantKey): `providerId:remoteId`, split on its
 *          first colon, or an identity id
 * @returns {Object} the participant a split removes
 */
function toRemovedAttendee(participantKey) {
  const separator = participantKey.indexOf(':');
  return {
    identity: separator < 0 ? {id: participantKey} : {
      providerId: participantKey.substring(0, separator),
      remoteId: participantKey.substring(separator + 1),
    },
  };
}

export function createEvent(event) {
  if (!Object.prototype.hasOwnProperty.call(event, 'sendInvitation')) {
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
        return toSaveError(resp, 'Error creating event');
      }
    });
}

/**
 * Saves an event as a whole.
 *
 * @param {Object} event the event to save
 * @param {Boolean} confirmClose whether the user confirmed that the save closes
 *          an open event with a limit, its waiting list then confirmed: without
 *          it, such a save is refused with agenda.capacity.closeNeedsConfirmation
 * @returns {Promise} the event saved
 */
export function updateEvent(event, confirmClose) {
  if (!Object.prototype.hasOwnProperty.call(event, 'sendInvitation')) {
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
      return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events?confirmClose=${!!confirmClose}`, {
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
        return toSaveError(resp, 'Error updating event');
      }
    });
}

/**
 * @param {Number} eventId the event answered, a series for several dates
 * @param {String} occurrenceId the date answered, if one is
 * @param {String} response the answer given, WAITING asking for a seat as
 *        ACCEPTED does
 * @param {Boolean} upcoming whether the answer holds for that date and the
 *        ones after it
 * @returns {Promise} resolved with what the server decided: the answer stored
 *          at the date answered (a Yes on a full event waits) and the first
 *          date an answer for several dates waits for, null when none
 */
export function sendEventResponse(eventId, occurrenceId, response, upcoming) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}/response/send?response=${response}&occurrenceId=${occurrenceId || ''}&upcoming=${upcoming || false}`, {
    method: 'GET',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
  }).then((resp) => {
    if (!resp || !resp.ok) {
      throw new Error('Error sending event response');
    }
    return resp.json().catch(() => null);
  });
}

export function updateEventFields(event, eventFields, updateAllOccurrences, sendInvitations, confirmClose) {
  const eventId = event.id;
  eventFields = formatEventToSave(eventFields);

  const saveWebConferencePromises = getSaveAllWebConferencesPromises(event);

  return Promise.all(saveWebConferencePromises)
    .catch(e => {
      console.error('Error updating web conferencing, delete them', e);
    })
    .then(() => {
      const formData = new FormData();
      Object.keys(eventFields).forEach(fieldName => {
        formData.append(fieldName, eventFields[fieldName]);
      });

      updateAllOccurrences = !!updateAllOccurrences;
      sendInvitations = !!sendInvitations;

      return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}?updateAllOccurrences=${updateAllOccurrences}&timeZoneId=${USER_TIMEZONE_ID}&sendInvitations=${sendInvitations}&confirmClose=${!!confirmClose}`, {
        method: 'PATCH',
        credentials: 'include',
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: new URLSearchParams(formData).toString(),
      });
    }).then((resp) => {
      if (resp && resp.ok) {
        return resp.json();
      } else {
        return toSaveError(resp, 'Error updating event');
      }
    });
}

/**
 * A refused save carries a message code in its body (agenda.openEvent.notAllowed,
 * agenda.capacity.belowAccepted...), or an object holding the code and the day,
 * yyyy-MM-dd, of the date of a series that blocks a limit: handed to the
 * caller, which may translate it instead of showing a generic error.
 *
 * @param {Response} resp the response of the refused save, or nothing
 * @param {String} message the error's message
 * @returns {Promise} rejected with an error carrying the status, the code and
 *          the day named, if any
 */
function toSaveError(resp, message) {
  if (!resp) {
    return Promise.reject(new Error(message));
  }
  return resp.text().then(body => {
    const error = new Error(message);
    error.status = resp.status;
    const text = (body || '').trim();
    if (text.startsWith('{')) {
      // An object without a code, the body of an invalid event, has none
      const refusal = parseRefusal(text);
      error.code = refusal.code;
      error.date = refusal.date;
    } else {
      error.code = text.replace(/^"|"$/g, '');
    }
    throw error;
  });
}

function parseRefusal(text) {
  try {
    return JSON.parse(text) || {};
  } catch (e) {
    return {};
  }
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

export function deleteEvent(eventId, delay) {
  if (delay > 0) {
    localStorage.setItem('agendaDeletedEvents', eventId);
  }
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}?timeZoneId=${USER_TIMEZONE_ID}&delay=${delay || 0}`, {
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
        return Promise.all(deleteAllWebConferencesPromises)
          .finally(() => event);
      }
    });
}

export function undoDeleteEvent(eventId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}/undoDelete`, {
    method: 'POST',
    credentials: 'include',
  })
    .then((resp) => {
      if (resp && resp.ok) {
        localStorage.removeItem('agendaDeletedEvents');
      } else {
        throw new Error('Error undoing deleting event');
      }
    });
}

export function voteEventDate(eventId, dateOptionId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}/votes/${dateOptionId}`, {
    method: 'POST',
    credentials: 'include',
  })
    .then((resp) => {
      if (!resp || !resp.ok) {
        throw new Error('Error voting on event');
      }
    });
}

export function saveEventVotes(eventId, dateOptionIds) {
  const formData = new FormData();
  Object.keys(dateOptionIds).forEach(dateOptionId => {
    formData.append('dateOptionId', dateOptionIds[dateOptionId]);
  });
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}/votes`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded'
    },
    body: new URLSearchParams(formData).toString(),
  })
    .then((resp) => {
      if (!resp || !resp.ok) {
        throw new Error('Error voting on event');
      }
    });
}

export function selectEventDate(eventId, dateOptionId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}/selectDateOption/${dateOptionId}`, {
    method: 'POST',
    credentials: 'include',
  })
    .then((resp) => {
      if (!resp || !resp.ok) {
        throw new Error('Error selecting an event date');
      } else {
        getEventById(eventId,'all').then(event => {
          const saveWebConferencePromises = getSaveAllWebConferencesPromises(event);
          return Promise.all(saveWebConferencePromises);
        });
      }
    });
}

export function dismissEventDate(eventId, dateOptionId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}/votes/${dateOptionId}`, {
    method: 'DELETE',
    credentials: 'include',
  })
    .then((resp) => {
      if (!resp || !resp.ok) {
        throw new Error('Error voting on event');
      }
    });
}

export function getDatePolls(ownerId, offset, limit, expand) {
  offset = offset || 0;
  limit = limit || 0;
  expand = expand || '';
  const formData = new FormData();
  formData.append('offset',offset);
  formData.append('limit',limit);
  formData.append('expand',expand);
  if (ownerId) {
    formData.append('ownerIds',ownerId);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/datePolls?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error getting pending date poll list');
    }
  }).then((data) => {
    let events = data && data.events || [];
    let deletedEventId = localStorage.getItem('agendaDeletedEvents');
    if (deletedEventId) {
      deletedEventId = Number(deletedEventId);
      events = events.filter(event => Number(event.id) !== deletedEventId && (!event.parent || Number(event.parent.id) !== deletedEventId));
      data.events = events;
    }
    return data;
  });
}

export function getDatePollsByDates(start, end, expand) {
  expand = expand || '';
  const formData = new FormData();
  formData.append('start',start);
  formData.append('end',end);
  formData.append('expand',expand);
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/datePolls?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error getting pending date poll list');
    }
  }).then((data) => {
    let events = data && data.events || [];
    let deletedEventId = localStorage.getItem('agendaDeletedEvents');
    if (deletedEventId) {
      deletedEventId = Number(deletedEventId);
      events = events.filter(event => Number(event.id) !== deletedEventId && (!event.parent || Number(event.parent.id) !== deletedEventId));
      data.events = events;
    }
    return data;
  });
}

export function countDatePolls(ownerId) {
  const formData = new FormData();
  if (ownerId) {
    formData.append('ownerIds',ownerId);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/datePolls?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error getting pending date poll count');
    }
  }).then(eventsList => {
    return eventsList && eventsList.size || 0;
  });
}

export function getPendingEvents(ownerId, offset, limit, expand) {
  offset = offset || 0;
  limit = limit || 0;
  expand = expand || '';
  const formData = new FormData();
  formData.append('offset',offset);
  formData.append('limit',limit);
  formData.append('expand',expand);
  if (ownerId) {
    formData.append('ownerIds',ownerId);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/pending?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error getting pending event list');
    }
  }).then((data) => {
    let events = data && data.events || [];
    let deletedEventId = localStorage.getItem('agendaDeletedEvents');
    if (deletedEventId) {
      deletedEventId = Number(deletedEventId);
      events = events.filter(event => Number(event.id) !== deletedEventId && (!event.parent || Number(event.parent.id) !== deletedEventId));
      data.events = events;
    }
    return data;
  });
}

export function countPendingEvents(ownerId) {
  const formData = new FormData();
  if (ownerId) {
    formData.append('ownerIds',ownerId);
  }
  const params = new URLSearchParams(formData).toString();
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/pending?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error getting pending event count');
    }
  }).then(eventsList => {
    return eventsList && eventsList.size || 0;
  });
}

export function generateICS(eventId, timeZoneId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/ics?eventId=${eventId}&timeZoneId=${timeZoneId}`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.text();
    } else {
      throw new Error(`Error getting ICS content for event ${eventId}`);
    }
  });
}

/**
 * Downloads the participant list of a date of an event as a CSV file. The
 * server writes the file and checks the right to export it; the browser
 * follows a plain link to it and saves it under the name the server gives.
 *
 * @param {Number} eventId the event, the series or the row of a date
 * @param {String} occurrenceId occurrence id of a date of the series that has
 *          no row of its own, null otherwise
 * @param {String} timeZoneId the user's zone, dating the file name
 * @returns {void}
 */
export function exportEventAttendees(eventId, occurrenceId, timeZoneId) {
  const params = new URLSearchParams();
  if (occurrenceId) {
    params.append('occurrenceId', occurrenceId);
  }
  if (timeZoneId) {
    params.append('timeZoneId', timeZoneId);
  }
  const link = document.createElement('a');
  link.href = `/agenda/rest/events/${eventId}/attendees/export?${params.toString()}`;
  link.setAttribute('download', '');
  document.body.appendChild(link);
  link.click();
  link.remove();
}

export function deleteContent(contentId, delay) {
  return fetch(`/content/rest/contents/${contentId}?type=article&delay=${delay || 0}`, {
    credentials: 'include',
    method: 'DELETE'
  }).then((resp) => {
    if (!resp?.ok) {
      throw new Error('Error when deleting content');
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
    } else if (recurrence.frequency === 'YEARLY') {
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
  // The sync below rewrites the call's participant list out of event.attendees,
  // so an entity that does not carry one has nothing to say about its
  // conferences and must not speak for them: an absent attendee list is not an
  // empty one. The parent of a recurrent event and the exceptional occurrences
  // are exactly that — the expand fills attendees on the top-level entity only
  // — and a remote-id patch routes them through here (EXO-90015). The other
  // callers pass a form event or an expand=all one, which always carries the
  // list, empty or not.
  if (event.conferences && event.conferences.length && event.attendees) {
    event.conferences.forEach(conference => {
      const saveWebConferencePromise = saveEventWebConferencing(event, conference);
      saveWebConferencePromises.push(saveWebConferencePromise);
    });
  }
  return saveWebConferencePromises;
}
