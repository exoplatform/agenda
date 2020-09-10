import {toRFC3339} from './AgendaUtils.js';

export function getEvents(query, ownerIds, attendeeIdentityId, start, end) {
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

export function updateEvent(event) {
  event = Object.assign({}, event);
  delete event.creator;
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

export function getEventById(eventId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/events/${eventId}`, {
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
