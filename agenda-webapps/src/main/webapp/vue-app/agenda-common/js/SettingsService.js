export function saveUserSettings(settings) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/settings`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(settings),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function getUserSettings() {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/settings`, {
    method: 'GET',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error getting settings', resp);
    }
  }).then(resp => {
    return resp;
  });
}
