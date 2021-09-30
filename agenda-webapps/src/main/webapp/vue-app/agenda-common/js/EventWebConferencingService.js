export function checkWebConferencingEnabled() {
  return loadWebContferencing();
}

export function getAllProviders() {
  return checkWebConferencingEnabled()
    .then(enabled => enabled && global.webConferencing.getAllProviders() || null);
}

export function deleteEventWebConferencing(conference) {
  const providerType = conference && conference.type;
  if (!providerType || !global.webConferencing || !global.webConferencing.deleteCall || !conference.url) {
    return Promise.resolve(null);
  }
  return deleteConference(conference.url);
}

export function saveEventWebConferencing(event, conference) {
  const providerType = conference && conference.type;
  if (!providerType) {
    return Promise.resolve(null);
  } else if (providerType === 'manual') {
    return Promise.resolve(conference);
  } else if (!global.webConferencing) {
    return Promise.resolve(null);
  }
  if (conference.url) {
    if (global.webConferencing.updateCall) {
      // Update existing conference
      return updateConference(event, conference);
    } else {
      return Promise.resolve(null);
    }
  } else {
    if (global.webConferencing.addCall) {
      // Create new conference
      return createConference(event, conference);
    } else {
      return Promise.resolve(null);
    }
  }
}

function deleteConference(provider, url) {
  return global.webConferencing.getCallId(url)
    .then(callId => {
      if (!callId) {
        // The call is already deleted or inexistant
        return;
      }
      return global.webConferencing.deleteCall(callId);
    });
}

function createConference(event, conference) {
  // FIXME : Web conferencing uses userName for users and Space Identity id for spaces
  return getAllIdentities(event.attendees)
    .then(identities => {
      const participants = identities.filter(identity => identity && identity.providerId === 'organization').map(identity => identity.remoteId);
      const spaces = identities.filter(identity => identity && identity.providerId === 'space').map(identity => identity.id);
      const startDate = new Date(event.startDate);
      const endDate = event.endDate && new Date(event.endDate) || event.recurrence && event.recurrence.until && new Date(event.recurrence.until) || null;
      return global.webConferencing.addCall({
        title: event.title,
        owner: event.calendar.owner.id,
        ownerType: 'space_event',
        provider: conference.type,
        participants: participants && participants.join(';') || null,
        spaces: spaces && spaces.join(';') || null,
        group: true,
        startDate,
        endDate,
      });
    })
    .then(callDetails => {
      conference.url = callDetails.url;
      return conference;
    });
}

function updateConference(event, conference) {
  let callId = null;
  return global.webConferencing.findCallId(conference.url, conference.type)
    .then(data => {
      if (!data) {
        throw new Error(`Conference with url ${conference.url} doesn't exist. Creating new one.`);
      }
      callId = data;
      return getAllIdentities(event.attendees);
    })
    .then(identities => {
      // FIXME : Web conferencing uses userName for users and Space Identity id for spaces
      const participants = identities.filter(identity => identity && identity.providerId === 'organization').map(identity => identity.remoteId);
      const spaces = identities.filter(identity => identity && identity.providerId === 'space').map(identity => identity.id);
      const startDate = new Date(event.startDate);
      const endDate = event.endDate && new Date(event.endDate) || event.recurrence && event.recurrence.until && new Date(event.recurrence.until) || null;
      return global.webConferencing.updateCall(callId, {
        title: event.title,
        owner: event.calendar.owner.id,
        ownerType: 'space_event',
        provider: conference.type,
        participants: participants && participants.join(';') || null,
        spaces: spaces && spaces.join(';') || null,
        group: true,
        startDate,
        endDate,
      });
    })
    .then(callDetails => {
      conference.url = callDetails.url;
      return conference;
    })
    .catch(() => createConference(event, conference));
}

function getAllIdentities(attendees) {
  const promises = [];
  attendees.forEach(attendee => {
    promises.push(Vue.prototype.$identityService.getIdentityByProviderIdAndRemoteId(attendee.identity.providerId, attendee.identity.remoteId));
  });
  return Promise.all(promises);
}

function loadWebContferencing() {
  return new Promise(resolve => {
    // Load lazily webconferencing API
    if (window.require.defined('SHARED/webConferencing')) {
      window.require(['SHARED/webConferencing'], webConferencingAPI => {
        global.webConferencing = webConferencingAPI;
        return resolve(!!webConferencingAPI);
      });
    }
    return resolve(!!global.webConferencing);
  });
}

loadWebContferencing();