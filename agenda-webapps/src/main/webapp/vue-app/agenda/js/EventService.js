import {toRFC3339} from './AgendaUtils.js';

export function getEvents(query, ownerIds, attendeeIdentityId, start, end, expand) {
  if (typeof start === 'object') {
    start = toRFC3339(start);
    end = toRFC3339(end);
  }

  let params = {
    query: query || '',
    start: start,
    end: end,
  };

  if (ownerIds && ownerIds.length) {
    params.ownerIds = ownerIds;
  }

  if (expand) {
    params.expand = expand;
  }

  if (attendeeIdentityId) {
    params.attendeeIdentityId = attendeeIdentityId;
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

export function createEvent(event) {
  event.calendar = Object.assign({}, event.calendar);
  event.calendar.owner = event.calendar.owner && {
    id: event.calendar.owner.id,
    providerId: event.calendar.owner.providerId,
    remoteId: event.calendar.owner.remoteId,
  };

  if (event.parent) {
    event.parent = {
      id: event.parent.id,
    };
    event.recurrence = null;
  }

  event.creator = null;
  if (event.attendees && event.attendees.length) {
    event.attendees.forEach(attendee => {
      attendee.identity = {
        id: attendee.identity.id || 0,
        providerId: attendee.identity.providerId,
        remoteId: attendee.identity.remoteId,
      };
    });
  }

  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(event),
  }).then((resp) => {
    if (!resp || !resp.ok) {
      throw new Error('Error creating event');
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
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error sending event response');
    }
  });
}

export function updateEvent(event) {
  event = Object.assign({}, event);
  event.calendar = Object.assign({}, event.calendar);
  event.calendar.owner = event.calendar.owner && {
    id: event.calendar.owner.id,
    providerId: event.calendar.owner.providerId,
    remoteId: event.calendar.owner.remoteId,
  };

  if (event.parent) {
    event.parent = {
      id: event.parent.id,
    };
    event.recurrence = null;
  }

  event.creator = null;
  if (event.calendar && event.calendar.owner) {
    event.calendar.owner = {
      id: event.calendar.owner.id,
      remoteId: event.calendar.owner.remoteId,
      providerId: event.calendar.owner.providerId,
    };
  }

  if (event.attendees && event.attendees.length) {
    event.attendees.forEach(attendee => {
      attendee.identity = {
        id: attendee.identity.id || 0,
        providerId: attendee.identity.providerId,
        remoteId: attendee.identity.remoteId,
      };
    });
  }

  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(event),
  }).then((resp) => {
    if (!resp || !resp.ok) {
      throw new Error('Error creating event');
    }
  });
}

export function getEventById(eventId, expand) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}?expand=${expand || ''}`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error getting event');
    }
  });
}

export function deleteEvent(eventId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}`, {
    method: 'DELETE',
    credentials: 'include',
  }).then((resp) => {
    if (!resp || !resp.ok) {
      throw new Error('Error deleting event');
    }
  });
}
