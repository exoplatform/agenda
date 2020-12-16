let webConferencing = null;

export function isWebConferencingEnabled() {
  return !!webConferencing;
}

export function getAllProviders() {
  if (isWebConferencingEnabled()) {
    return webConferencing.getAllProviders();
  }
  return Promise.resolve(null);
}

export function deleteEventWebConferencing(conference) {
  const providerType = conference && conference.type;
  if (!providerType || !webConferencing || !webConferencing.deleteCall || !conference.url) {
    return Promise.resolve(null);
  }
  return deleteConference(conference.url);
}

export function saveEventWebConferencing(event, conference) {
  const providerType = conference && conference.type;
  if (!providerType || !webConferencing) {
    return Promise.resolve(null);
  } else if (providerType === 'manual') {
    return Promise.resolve(conference);
  }
  if (conference.url) {
    if (webConferencing.updateCall) {
      // Update existing conference
      return updateConference(event, conference);
    } else {
      return Promise.resolve(null);
    }
  } else {
    if (webConferencing.addCall) {
      // Create new conference
      return createConference(event, conference);
    } else {
      return Promise.resolve(null);
    }
  }
}

function deleteConference(provider, url) {
  return webConferencing.getCallId(url)
    .then(callId => {
      if (!callId) {
        // The call is already deleted or inexistant
        return;
      }
      return webConferencing.deleteCall(callId);
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
      return webConferencing.addCall({
        title: event.title,
        owner: event.calendar.owner.remoteId,
        ownerType: 'space_event',
        provider: conference.type,
        participants: participants.join(';'),
        spaces: spaces.join(';'),
        group : true,
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
  return webConferencing.findCallId(conference.url)
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
      return webConferencing.updateCall(callId, {
        title: event.title,
        owner: event.calendar.owner.remoteId,
        ownerType: 'space_event',
        provider: conference.type,
        participants: participants.join(';'),
        spaces: spaces.join(';'),
        group : true,
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

// Load lazily webconferencing API
if (window.require.defined('SHARED/webConferencing')) {
  window.require(['SHARED/webConferencing'], webConferencingAPI => {
    webConferencing = webConferencingAPI;
  });
}
