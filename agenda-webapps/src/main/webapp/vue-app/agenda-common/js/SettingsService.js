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


export function saveRemoteProviderStatus(ConnectorName, ConnectorStatus) {
  const formData = new FormData();
  formData.append('connectorName', ConnectorName);
  formData.append('enabled', ConnectorStatus);

  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/settings/connector/status`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: new URLSearchParams(formData).toString(),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function getSettingsValue() {
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
