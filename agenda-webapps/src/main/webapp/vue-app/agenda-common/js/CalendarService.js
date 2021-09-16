const CALENDARS_CACHE = {};

export function getCalendars(offset, limit, returnSize, spaceIdentityIds) {
  let params = {
    offset: offset,
    limit: limit,
    returnSize: !!returnSize,
  };

  if (spaceIdentityIds && spaceIdentityIds.length) {
    params.ownerIds = spaceIdentityIds;
  }

  params = $.param(params, true);

  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/calendars?${params}`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error retrieving list of calendars');
    }
  });
}

export function getCalendarById(calendarId, useCache) {
  if (useCache && CALENDARS_CACHE[calendarId]) {
    return Promise.resolve(CALENDARS_CACHE[calendarId]);
  }
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/calendars/${calendarId}`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error(`Error getting calendar by id ${calendarId}`);
    }
  }).then((calendar) => CALENDARS_CACHE[calendarId] = calendar);
}

export function saveCalendar(calendar) {
  calendar.owner = calendar.owner && {
    id: calendar.owner.id,
    providerId: calendar.owner.providerId,
    remoteId: calendar.owner.remoteId,
  };
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/calendars`, {
    method: calendar.id ? 'PUT' : 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(calendar),
  }).then((resp) => {
    if (!resp || !resp.ok) {
      throw new Error('Error saving calendar');
    }
  });
}
