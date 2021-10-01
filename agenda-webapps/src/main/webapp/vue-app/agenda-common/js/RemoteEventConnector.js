// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

import {getEventExceptionalOccurrences, updateEventFields, createEvent} from './EventService.js';

export function removeEventFromConnector(connector, event, allRecurrentEvent) {
  return modifyEventOnConnector(connector, event, allRecurrentEvent, true);
}

export function pushEventToConnector(connector, event, allRecurrentEvent) {
  return modifyEventOnConnector(connector, event, allRecurrentEvent, false);
}

function modifyEventOnConnector(connector, event, allRecurrentEvent, deleteEvent) {
  if (!connector) {
    return Promise.reject(new Error('Connector is mandatory'));
  }
  if (!connector.connected || !connector.user || !connector.isSignedIn) {
    return Promise.reject(new Error('Connector is not connected'));
  }
  if (!event) {
    return Promise.reject(new Error('Event is mandatory'));
  }

  event = allRecurrentEvent && event.parent ? event.parent : event;
  let connectorParentEvent = null;

  connector.pushing = true;
  const connectorParentEventId = event.parent && event.parent.remoteId || null;
  const connectorMethod = deleteEvent ? connector.deleteEvent : connector.pushEvent;

  return connectorMethod.apply(connector, [event, connectorParentEventId])
    .then((connectorEvent) => {
      connectorParentEvent = connectorEvent;
      return updateEventRemoteInformation(connector, event, connectorParentEvent);
    })
    .then(event => {
      if (allRecurrentEvent && event.recurrence) {
        return getEventExceptionalOccurrences(event.id)
          .then(exceptionalOcuurences => {
            if (exceptionalOcuurences && exceptionalOcuurences.length) {
              const promises = [];
              exceptionalOcuurences.forEach(exceptionalOccurrence => {
                const exceptionalOccurrenceRemoteIdUpdate = connectorMethod.apply(connector, [exceptionalOccurrence, connectorParentEvent && connectorParentEvent.id || connectorParentEventId])
                  .then((connectorExceptionalOccurrence) => {
                    // Avoid deleting remote connector event information if it's definitely
                    // remotely deleted
                    if (deleteEvent || connectorExceptionalOccurrence) {
                      return updateEventRemoteInformation(connector, exceptionalOccurrence, connectorExceptionalOccurrence);
                    }
                  });
                promises.push(exceptionalOccurrenceRemoteIdUpdate);
              });
              return Promise.all(promises);
            }
          });
      }
    })
    .finally(() => connector.pushing = false);
}

function updateEventRemoteInformation(connector, event, connectorEvent) {
  const eventId = event && event.id;
  const remoteId = connectorEvent && connectorEvent.id || '';
  const remoteProviderName = connectorEvent && connector && connector.name || '';

  if (eventId) {
    return updateEventFields(eventId, {
      remoteId,
      remoteProviderName,
    })
      .then(() => {
        event.remoteId = remoteId;
        event.remoteProviderName = remoteProviderName;
        return event;
      });
  } else {
    const newExceptionalEvent = Object.assign({}, event);
    newExceptionalEvent.remoteId =  remoteId;
    newExceptionalEvent.remoteProviderName = remoteProviderName;
    return createEvent(newExceptionalEvent);
  }
}
