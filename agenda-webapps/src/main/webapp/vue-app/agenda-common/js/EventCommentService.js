export function getEventComments(activityId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/activities/${activityId}/comments?expand=identity,likes,subComments&_=${Date.now()}`, {
    credentials: 'include',
    method: 'GET',
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error(`Error getting event activity comments for event ${activityId}`);
    }
  });
}

export function createEventActivity(eventId, spaceId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/spaces/${spaceId}/activities`, {
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    method: 'POST',
    body: JSON.stringify({
      'title': '',
      'type': 'exo:agendaDatePoll',
      'templateParams': {
        'eventId': String(eventId),
      },
      'hidden': true,
    })
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error(`Error posting activity for event ${eventId}`);
    }
  });
}

export function createEventComment(activityId, comment) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/v1/social/activities/${activityId}/comments`, {
    headers: {
      'Content-Type': 'application/json'
    },
    credentials: 'include',
    method: 'POST',
    body: JSON.stringify({ 
      poster: eXo.env.portal.userName,
      title: comment,
    }),
  }).then((resp) => {
    if (resp && resp.ok) {
      return resp.json();
    } else {
      throw new Error(`Error posting activity for event ${activityId}`);
    }
  });
}

export function deleteEventComment(activityId, commentId) {
  return fetch(`${eXo.env.portal.context}/${eXo.env.portal.rest}/portal/social/activities/${activityId}/comments/destroy/${commentId}.json`, {
    credentials: 'include',
    method: 'POST',
  }).then((resp) => {
    if (!resp || !resp.ok) {
      throw new Error(`Error deleting comment with id ${commentId}`);
    }
  });
}