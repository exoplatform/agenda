import {toRFC3339} from './AgendaUtils.js';

export function getEvents(query, ownerId, start, end) {
  if (typeof start === 'object') {
    start = toRFC3339(start);
    end = toRFC3339(end);
  }

  const params = $.param({
    query: query || '',
    ownerId: ownerId,
    start: start,
    end: end,
  });

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
    body: JSON.stringify(event),
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error creating event');
    }
  });
}