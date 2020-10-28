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

export function saveAgendaSettings(settingsValues) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/settings/USER,${eXo.env.portal.userName}/APPLICATION,Agenda/agendaSettings`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      value: JSON.stringify(settingsValues),
    }),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error saving agenda settings', resp);
    }
  });
}

export function getAgendaSettings() {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/settings/USER,${eXo.env.portal.userName}/APPLICATION,Agenda/agendaSettings`, {
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
      throw new Error('Error getting agenda settings');
    }
  }).then(resp => {
    return resp;
  });
}
export function saveAgendaConnectorsSettings(settingsValues) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/settings/USER,${eXo.env.portal.userName}/APPLICATION,Agenda/agendaConnectorsSettings`, {
    method: 'PUT',
    credentials: 'include',
    headers: {
      Accept: 'application/json',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      value: JSON.stringify(settingsValues),
    }),
  }).then(resp => {
    if (!resp || !resp.ok) {
      throw new Error('Error saving agenda settings', resp);
    }
  });
}

export function getAgendaConnectorsSettings() {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/settings/USER,${eXo.env.portal.userName}/APPLICATION,Agenda/agendaConnectorsSettings`, {
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
      throw new Error('Error getting agenda settings');
    }
  }).then(resp => {
    return resp;
  });
}