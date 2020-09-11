export function getCalendars(offset, limit, returnSize) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/calendars?offset=${offset}&limit=${limit}&returnSize=${returnSize && true || false}`, {
    method: 'GET',
    credentials: 'include',
  }).then((resp) => {
    if (!resp || !resp.ok) {
      throw new Error('Error creating event');
    } else {
      return resp.json();
    }
  });
}

export function saveCalendar(calendar) {
  calendar.owner = calendar.owner && {
    id: calendar.owner.id,
  };
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/calendars`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(calendar),
  }).then((resp) => {
    if (!resp || !resp.ok) {
      throw new Error('Error updating calendar');
    }
  });
}