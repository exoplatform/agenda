export function deleteEventWebConferencing(conference) {
  const providerType = conference && conference.type;
  if (!providerType || !eXo.webConferencing || !conference.url) {
    return Promise.resolve(null);
  }
  return eXo.webConferencing.getAllProviders()
    .then(providers => {
      const provider = providers && providers.find(confProvider => confProvider && confProvider.getType() === providerType && confProvider.isInitialized && confProvider.getCallId);
      if (provider) {
        return deleteConference(provider, conference.url);
      }
    });
}

export function saveEventWebConferencing(event, conference) {
  const providerType = conference && conference.type;
  if (!providerType || !eXo.webConferencing) {
    return Promise.resolve(null);
  }
  return eXo.webConferencing.getAllProviders()
    .then(providers => {
      const provider = providers && providers.find(confProvider => confProvider && confProvider.getType() === providerType && confProvider.isInitialized && confProvider.getCallId);
      if (!provider) {
        return null;
      }
      if (conference.url) {
      // Update existing conference
        return updateConference(provider, event, conference);
      } else {
      // Create new conference
        return createConference(provider, event, conference);
      }
    });
}

function deleteConference(provider, url) {
  return provider.getCallId(url)
    .then(callId => {
      if (!callId) {
        // The call is already deleted an inexistant
        return;
      }
      return provider.deleteCall(callId);
    });
}

function createConference(provider, event, conference) {
  const identityIds = event.attendees.map(attendee => attendee.id);
  const startDate = new Date(event.startDate);
  const endDate = event.endDate && new Date(event.endDate) || event.recurrence && event.recurrence.until && new Date(event.recurrence.until) || null;
  return provider.addCall(event.title, identityIds, startDate, endDate)
    .then(callDetails => {
      conference.url = callDetails.url;
      return conference;
    });
}

function updateConference(provider, event, conference) {
  return provider.getCallId(conference.url)
    .then(callId => {
      if (!callId) {
        throw new Error(`Conference with url ${conference.url} doesn't exist. Creating new one.`);
      }
      const identityIds = event.attendees.map(attendee => attendee.id);
      const startDate = new Date(event.startDate);
      const endDate = event.endDate && new Date(event.endDate) || event.recurrence && event.recurrence.until && new Date(event.recurrence.until) || null;
      return provider.updateCall(callId, event.title, identityIds, startDate, endDate);
    })
    .then(callDetails => {
      conference.url = callDetails.url;
      return conference;
    })
    .catch(() => createConference(provider, event, conference));
}
