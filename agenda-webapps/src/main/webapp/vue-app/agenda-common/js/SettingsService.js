export function setSettingsValue(contextKey, contextValue, scopeKey, scopeValue, key, value) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/settings/${contextKey},${contextValue}/${scopeKey},${scopeValue}/${key}`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      value: JSON.stringify(value),
    }),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}


export function getSettingsValue(contextKey, contextValue, scopeKey, scopeValue, key) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/settings/${contextKey},${contextValue}/${scopeKey},${scopeValue}/${key}`, {
    method: 'GET',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else if (resp && resp.status === 404) {
      return null;
    } else {
      throw new Error('Error getting settings', resp);
    }
  }).then(resp => {
    return resp;
  });
}
