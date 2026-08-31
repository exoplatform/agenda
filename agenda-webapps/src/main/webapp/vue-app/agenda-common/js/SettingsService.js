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

/**
 * Disconnects one connected account, or every account when no connector name
 * is given. Naming the connector is what lets one CalDAV account and one or
 * more remote accounts coexist: disconnecting Google must not take the CalDAV
 * account backing My Calendars with it.
 *
 * @param {String} connectorName name of the connector whose account is
 *          disconnected; absent, every connected account is
 * @returns {Promise} resolves when the account is removed
 */
export function resetUserConnector(connectorName) {
  const queryParam = connectorName && `?connectorName=${encodeURIComponent(connectorName)}` || '';
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/settings/connector${queryParam}`, {
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

export function saveEmbedMapProvider(providerId) {
  const formData = new FormData();
  formData.append('providerId', providerId);

  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/settings/embedMapProvider`, {
    method: 'PATCH',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams(formData).toString(),
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

export function removeEmbedMapProvider() {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/settings/embedMapProvider`, {
    method: 'DELETE',
    credentials: 'include',
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}

/**
 * Reads how widely the current user shares their busy time.
 *
 * Its own request, not a field of the settings payload: the value is stored
 * under its own key so that a settings save which does not carry it cannot
 * silently reset a disclosure choice.
 *
 * @returns {Promise<String>} 'everyone', 'shared-spaces' or 'nobody'
 */
export function getAvailabilitySharing() {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/settings/availabilitySharing`, {
    method: 'GET',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
    },
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
    return resp.json();
  }).then(setting => setting?.shareAvailability);
}

/**
 * Stores how widely the current user shares their busy time.
 *
 * @param {String} shareAvailability 'everyone', 'shared-spaces' or 'nobody'
 * @returns {Promise} resolves when the choice is stored
 */
export function saveAvailabilitySharing(shareAvailability) {
  const formData = new FormData();
  formData.append('shareAvailability', shareAvailability);

  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/agenda/settings/availabilitySharing`, {
    method: 'PATCH',
    credentials: 'include',
    headers: {
      'Content-Type': 'application/x-www-form-urlencoded',
    },
    body: new URLSearchParams(formData).toString(),
  }).then(resp => {
    if (!resp?.ok) {
      throw new Error('Response code indicates a server error', resp);
    }
  });
}
