import {getEventExceptionalOccurrences, updateEventFields, createEvent} from './EventService.js';

/**
 * Removes an event from the remote account behind a connector.
 *
 * @param {Object} connector the connected connector
 * @param {Object} event the eXo event whose remote copy must go
 * @param {Boolean} allRecurrentEvent whether the whole recurrent series is meant
 * @returns {Promise} resolves once the remote copy is removed
 */
export function removeEventFromConnector(connector, event, allRecurrentEvent) {
  return modifyEventOnConnector(connector, event, allRecurrentEvent, true);
}

/**
 * Pushes an event to the remote account behind a connector.
 *
 * @param {Object} connector the connected connector
 * @param {Object} event the eXo event to push
 * @param {Boolean} allRecurrentEvent whether the whole recurrent series is meant
 * @returns {Promise} resolves once the remote copy is written
 */
export function pushEventToConnector(connector, event, allRecurrentEvent) {
  return modifyEventOnConnector(connector, event, allRecurrentEvent, false);
}

/**
 * Filters the mirror calendar of a connector out of a calendar list. The
 * mirror only holds copies of meetings eXo already displays, so listing it —
 * in the left panel, in a picker — would render every accepted meeting
 * twice.
 *
 * The comparison is made on the href, never the display name: a user
 * renaming the mirror in their own client must not bring it back, and
 * nothing stops two collections sharing a name. Hrefs are compared as
 * decoded paths, so an encoding difference (%40 versus @) or a changed host
 * never breaks the match. A connector that exposes no mirror leaves the list
 * untouched.
 *
 * Only a DEDICATED mirror — a calendar the connector created to hold the
 * copies — is left out. A server that refuses creating calendars makes the
 * connector adopt an existing calendar of the user as the destination
 * instead, and that one must keep appearing everywhere it always did:
 * hiding it would make a calendar the user relies on — typically their
 * primary one — quietly disappear. Its pushed copies do not double the
 * grid: the display filters them out by the identifier stored at push time.
 * A connector that cannot tell the two apart keeps the previous behaviour,
 * excluding whatever mirror it names.
 *
 * @param {Object} connector the connector the calendars came from
 * @param {Array} calendars the calendars to filter
 * @returns {Promise<Array>} the calendars without the dedicated mirror
 */
export function excludeMirrorCalendar(connector, calendars) {
  if (!connector || typeof connector.getMirrorCalendarId !== 'function' || !calendars || !calendars.length) {
    return Promise.resolve(calendars || []);
  }
  return connector.getMirrorCalendarId()
    .then(mirrorCalendarId => mirrorCalendarId
      && isDedicatedMirror(connector, mirrorCalendarId)
      && calendars.filter(calendar => !isSameCalendarHref(calendar.id, mirrorCalendarId))
      || calendars)
    .catch(() => calendars);
}

/**
 * Whether the mirror a connector names is a calendar it created for the
 * copies, as opposed to an existing calendar it adopted. Asked of the
 * connector because only it knows how its mirror came to be; a connector
 * predating the distinction is read as dedicated, which is what every
 * mirror was before adoption existed.
 *
 * @param {Object} connector the connector naming the mirror
 * @param {String} mirrorCalendarId href of the mirror calendar
 * @returns {Boolean} true when the mirror holds nothing but copies
 */
function isDedicatedMirror(connector, mirrorCalendarId) {
  return typeof connector.isDedicatedMirrorCalendar !== 'function'
    || connector.isDedicatedMirrorCalendar(mirrorCalendarId);
}

/**
 * Whether two hrefs designate the same calendar collection, compared as
 * decoded, slash-trimmed paths.
 *
 * @param {String} firstHref first collection href
 * @param {String} secondHref second collection href
 * @returns {Boolean} true when both point at the same collection
 */
export function isSameCalendarHref(firstHref, secondHref) {
  return !!firstHref && !!secondHref && calendarHrefPath(firstHref) === calendarHrefPath(secondHref);
}

/**
 * The decoded path of a collection href, without a trailing slash — the part
 * that identifies the collection regardless of host or percent-encoding.
 *
 * @param {String} href collection URL or href
 * @returns {String} its decoded, slash-trimmed path
 */
function calendarHrefPath(href) {
  try {
    return decodeURIComponent(new URL(href, window.location.origin).pathname).replace(/\/+$/, '');
  } catch (e) {
    return href;
  }
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
    return updateEventFields(event, {
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
