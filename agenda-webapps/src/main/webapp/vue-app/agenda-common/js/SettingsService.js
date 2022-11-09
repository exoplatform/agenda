import {USER_TIMEZONE_ID} from './AgendaUtils.js';

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

export function saveRemoteProviderStatus(connectorName, connectorStatus, isOauth) {
  const formData = new FormData();
  formData.append('connectorName', connectorName);
  formData.append('enabled', !!connectorStatus);
  formData.append('isOauth', isOauth);

  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/settings/connector/status`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams(formData).toString(),
  }).then(resp => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function saveRemoteProviderApiKey(connectorName, apiKey) {
  const formData = new FormData();
  formData.append('connectorName', connectorName);
  formData.append('apiKey', apiKey || '');
  
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/settings/connector/apiKey`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams(formData).toString(),
  }).then(resp => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function saveRemoteProviderSecretKey(connectorName, secretKey) {
  const formData = new FormData();
  formData.append('connectorName', connectorName);
  formData.append('secretKey', secretKey || '');

  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/settings/connector/secretKey`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams(formData).toString(),
  }).then(resp => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error('Error while saving connector secretKey');
    }
  });
}

export function saveEnabledWebConferencingProvider(providerName) {
  const formData = new FormData();
  formData.append('providerName', providerName);
  
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/settings/webConferencing`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams(formData).toString(),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function saveTimeZone(timeZoneId) {
  const formData = new FormData();
  formData.append('timeZoneId', timeZoneId);

  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/settings/timeZone`, {
    method: 'PATCH',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams(formData).toString(),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function saveUserConnector(connectorName, connectorUserId) {
  const formData = new FormData();
  formData.append('connectorName', connectorName);
  formData.append('connectorUserId', connectorUserId);
  
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/settings/connector`, {
    method: 'PATCH',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams(formData).toString(),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function resetUserConnector() {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/settings/connector`, {
    method: 'DELETE',
    credentials: 'include',
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
  }).then(settings => {
    if (settings && (!settings.timeZoneId || settings.timeZoneId !== USER_TIMEZONE_ID)) {
      settings.timeZoneId = USER_TIMEZONE_ID;
      saveTimeZone(USER_TIMEZONE_ID);
    }
    return settings;
  }).then(resp => {
    return resp;
  });
}
