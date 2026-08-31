import {getEventExceptionalOccurrences, updateEventFields, createEvent} from './EventService.js';

/**
 * The in-flight or settled calendar listing of each connector, keyed by the
 * connector object itself so a rebuilt connector starts from a clean listing
 * and a discarded one is collected with it.
 */
const calendarListings = new WeakMap();

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
 * The calendar listing of a connector, fetched at most once per connector.
 *
 * Listing calendars is a live request to the remote account, and the calendar
 * of an event is asked for every preview that is opened. The *promise* is
 * memoised rather than its result, so ten previews opened in a row — or ten
 * opened while the first request is still in flight — cost one request.
 *
 * A failure is memoised as an empty listing rather than left to be retried:
 * an unreachable account would otherwise make every preview wait out the same
 * timeout before showing anything.
 *
 * @param {Object} connector the connector to list
 * @returns {Promise<Array>} its calendars, empty when it cannot list them
 */
function connectorCalendars(connector) {
  if (!connector || !connector.canListCalendars || typeof connector.listCalendars !== 'function') {
    return Promise.resolve([]);
  }
  if (!calendarListings.has(connector)) {
    calendarListings.set(connector, Promise.resolve()
      .then(() => connector.listCalendars())
      .then(calendars => calendars || [])
      .catch(error => {
        console.error('cannot list the calendars of', connector && connector.name, error);
        return [];
      }));
  }
  return calendarListings.get(connector);
}

/**
 * What the connected account calls the collection an event was read from.
 *
 * <p>
 * An event read live from an account holds no eXo calendar — only the href of
 * the collection it lives in — so the only thing that can name it is the
 * account itself. Matched on the href and never on position: a listing is not
 * ordered, and two collections may share a display name.
 *
 * <p>
 * Resolves to null rather than to a guess whenever the collection is absent
 * from the listing. That is not a theoretical case: a connector may read
 * events from collections it deliberately leaves out of its listing — the
 * CalDAV one omits the collections eXo has already materialised, and the ones
 * eXo itself created but no longer holds a binding for. The caller says
 * honestly that it cannot name it; it must not fall back to naming the
 * connector, which answers "how did this arrive" and not "where does it live".
 *
 * @param {Object} connector the connector the event was read from
 * @param {String} calendarHref href of the collection holding the event
 * @returns {Promise<String>} the collection's name, null when unnameable
 */
export function remoteCalendarName(connector, calendarHref) {
  if (!calendarHref) {
    return Promise.resolve(null);
  }
  return connectorCalendars(connector)
    .then(calendars => (calendars || []).find(calendar => isSameCalendarHref(calendar.id, calendarHref)))
    .then(calendar => calendar && calendar.name || null);
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

/**
 * How to phrase "when did this last synchronise", as a key of the Agenda
 * bundle and the number that goes in it.
 *
 * Shared by the settings row and the connectors drawer so the two never
 * disagree about what "just now" means. The phrasing lives in this bundle
 * rather than going through the platform's relative-time helper: that helper
 * resolves TimeConvert.* keys, which live in the commons bundle and are not
 * loaded on the pages either of these render in — they would show raw keys.
 *
 * Whether the state has been read at all is the caller's to know: this is
 * only asked once there is an answer, and a missing date here means the
 * account has never synchronised, not that nobody looked.
 *
 * @param {Date} lastSync when it last finished, null when it never has
 * @returns {Object} {key, count} to feed $t
 */
export function lastSyncPhrase(lastSync) {
  if (!lastSync) {
    return {key: 'agenda.connectors.lastSync.never'};
  }
  const minutes = Math.round((Date.now() - lastSync.getTime()) / 60000);
  if (minutes < 2) {
    return {key: 'agenda.connectors.lastSync.justNow'};
  } else if (minutes < 60) {
    return {key: 'agenda.connectors.lastSync.minutes', count: minutes};
  } else if (minutes < 1440) {
    return {key: 'agenda.connectors.lastSync.hours', count: Math.round(minutes / 60)};
  }
  return {key: 'agenda.connectors.lastSync.days', count: Math.round(minutes / 1440)};
}
