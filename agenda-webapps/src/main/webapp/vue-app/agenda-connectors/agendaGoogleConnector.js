export default {
  name: 'agenda.googleCalendar',
  description: 'agenda.googleCalendar.description',
  avatar: '/agenda/skin/images/Google.png',
  CLIENT_ID: null,
  DISCOVERY_DOCS: ['https://www.googleapis.com/discovery/v1/apis/calendar/v3/rest'],
  SCOPE_WRITE: 'https://www.googleapis.com/auth/calendar.events',
  canConnect: true,
  canPush: false,
  initialized: false,
  isSignedIn: false,
  pushing: false,
  init(connectionStatusChangedCallback, loadingCallback, apiKey) {
    if (!apiKey) {
      throw new Error('Google connector can\'t be enabled with empty Client API Key.');
    }
    this.CLIENT_ID = apiKey;

    // Already initialized
    if (this.initialized) {
      return;
    }
    this.initialized = true;
    this.connectionStatusChangedCallback = connectionStatusChangedCallback;
    this.loadingCallback = loadingCallback;

    initGoogleConnector(this);
  },
  connect(askWriteAccess) {
    const googleScope = this.SCOPE_WRITE;

    this.loadingCallback(this, true);
    let userEmail = null;
    // Return a Promise with connected username
    return this.gapi.auth2.getAuthInstance().signIn({
      scope: googleScope,
    })
      .then(currentUser => {
        userEmail = currentUser.getBasicProfile().getEmail();
        if (askWriteAccess && !this.canPush) {
          if (currentUser.hasGrantedScopes(this.SCOPE_WRITE)) {
            this.canPush = true;
          } else {
            return new Promise((resolve, reject) => {
              currentUser.grant({
                scope: googleScope,
              }).then(
                () => {
                  this.canPush = true;
                  resolve(userEmail);
                }
                ,(error) => {
                  this.canPush = false;
                  reject(error);
                }
                , this
              );
            });
          }
        }
      }).then(() => userEmail);
  },
  disconnect() {
    this.loadingCallback(this, true);
    if (this.gapi.auth2.getAuthInstance()) {
      return this.gapi.auth2.getAuthInstance().signOut();
    } else {
      return Promise.resolve(null);
    }
  },
  getEvents(periodStartDate, periodEndDate) {
    if (this.gapi && this.gapi.client && this.gapi.client.calendar) {
      const currentUser = this.gapi.auth2.getAuthInstance().currentUser.get();

      this.loadingCallback(this, true);
      return new Promise((resolve, reject) => {
        if (currentUser.hasGrantedScopes(this.SCOPE_WRITE)) {
          retrieveEvents(this, periodStartDate, periodEndDate)
            .then(gEvents => resolve(gEvents))
            .catch(e => {
              this.loadingCallback(this, false);
              reject(e);
            });
        } else {
          currentUser.grant({
            scope: this.SCOPE_WRITE
          }).then(
            () => retrieveEvents(this, periodStartDate, periodEndDate)
              .then(gEvents => resolve(gEvents))
              .catch(e => reject(e))
            ,(error) => reject(error)
          );
        }
      }).finally(() => this.loadingCallback(this, false));
    } else {
      return Promise.resolve(null);
    }
  },
  deleteEvent(event, connectorRecurringEventId) {
    return this.saveEvent(event, connectorRecurringEventId, true);
  },
  pushEvent(event, connectorRecurringEventId) {
    return this.saveEvent(event, connectorRecurringEventId, false);
  },
  saveEvent(event, connectorRecurringEventId, deleteEvent) {
    if (this.gapi && this.gapi.auth2.getAuthInstance()) {
      const currentUser = this.gapi.auth2.getAuthInstance().currentUser.get();

      this.pushing = true;
      return new Promise((resolve, reject) => {
        if (currentUser.hasGrantedScopes(this.SCOPE_WRITE)) {
          pushEventToGoogle(this, event, connectorRecurringEventId, deleteEvent)
            .then(gEvent => {
              resolve(gEvent);
            })
            .catch(error => reject(error));
        } else {
          currentUser.grant({
            scope: this.SCOPE_WRITE
          }).then(
            () => pushEventToGoogle(this, event, connectorRecurringEventId, deleteEvent)
              .then(gEvent => {
                resolve(gEvent);
              })
              .catch(error => reject(error))
            ,
            (error) => reject(error)
          ).catch(error => reject(error));
        }
      }).finally(() => this.pushing = false);
    }
    return Promise.reject(new Error('Not connected'));
  },
};

/**
 * @param {Object}
 *          connector Google Connector SPI
 * @param {Date}
 *          periodStartDate Start date of period of events to retrieve
 * @param {Date}
 *          periodEndDate End date of period of events to retrieve
 * @returns {Promise} a promise with list of Google events
 */
function retrieveEvents(connector, periodStartDate, periodEndDate) {
  return connector.gapi.client.calendar.events.list({
    'calendarId': 'primary',
    'timeMin': periodStartDate,
    'timeMax': periodEndDate,
    'singleEvents': true,
    'orderBy': 'startTime'
  }).then(events => events.result.items).then(events => {
    events.forEach(event => {
      event.allDay = !!event.start.date;
      event.start = event.start.dateTime || event.start.date;
      // Google api returns all day event with one day added for end date.
      const endDate = new Date(event.end.date);
      endDate.setDate(endDate.getDate()-1);
      event.end = event.allDay ? endDate : event.end.dateTime;
      event.name = event.summary;
      event.type = 'remoteEvent';
      event.color = '#FFFFFF';
    });
    connector.loadingCallback(connector, false);
    return events;
  });
}

/**
 * Load Google Connector API javascript and prepare user authentication and
 * authorization process
 * 
 * @param {Object}
 *          connector Google Connector SPI
 * @returns {void}
 */
function initGoogleConnector(connector) {
  // Called when the signed in status changes, to update the UI
  // appropriately. After a sign-in, the API is called.
  function updateSigninStatus(isSignedIn) {
    connector.isSignedIn = isSignedIn;
    try {
      if (isSignedIn) {
        const currentUser = connector.gapi.auth2.getAuthInstance().currentUser.get();
        connector.canPush = currentUser.hasGrantedScopes(connector.SCOPE_WRITE);
        connector.connectionStatusChangedCallback(connector, {
          user: currentUser.getBasicProfile().getEmail(),
          id: currentUser.getId(),
        });
      } else {
        connector.canPush = false;
        connector.connectionStatusChangedCallback(connector, false);
      }
    } finally {
      connector.loadingCallback(connector, false);
    }
  }

  connector.loadingCallback(connector, true);
  window.require(['https://apis.google.com/js/api.js'], () => {
    connector.gapi = gapi;
    connector.gapi.load('client:auth2', function() {
      gapi.client.init({
        clientId: connector.CLIENT_ID,
        discoveryDocs: connector.DISCOVERY_DOCS,
        scope: connector.SCOPE_WRITE,
      }).then(function () {
        // Listen for sign-in state changes.
        gapi.auth2.getAuthInstance().isSignedIn.listen(updateSigninStatus);

        // Handle the initial sign-in state.
        updateSigninStatus(gapi.auth2.getAuthInstance().isSignedIn.get());
      }, function(error) {
        connector.loadingCallback(connector, false);
        connector.connectionStatusChangedCallback(connector, false, error);
      });
    });
  }, (error) => {
    connector.canConnect = false;
    connector.loadingCallback(connector, false);
    console.error('Error retrieving Google API Javascript', error);
  });
}

/**
 * Push event into Google account
 * 
 * @param {Object}
 *          connector Google Connector SPI
 * @param {Object}
 *          event Agenda event
 * @param {String}
 *          connectorRecurringEventId Connector parent recurrent event
 *          Identifier
 * @param {Boolean}
 *          deleteEvent whether to delete or save event status
 * @returns {void}
 */
function pushEventToGoogle(connector, event, connectorRecurringEventId, deleteEvent) {
  const connectorEvent = buildConnectorEvent(event, connectorRecurringEventId);
  let retrievingEventPromise = null;
  const isExceptionalOccurrence = connectorRecurringEventId && event.occurrence && event.occurrence.id;
  const isRemoteEvent = event.remoteId && event.remoteProviderName === connector.name;
  const isDeleteEvent = deleteEvent || event.status.toLowerCase() === 'cancelled';

  if (isExceptionalOccurrence || isRemoteEvent || isDeleteEvent) {
    const options = {
      'calendarId': 'primary',
      'showDeleted': true,
    };
    if (isExceptionalOccurrence) {
      options.eventId = connectorRecurringEventId;
      options.recurringEventId = connectorRecurringEventId;
      options.originalStart = event.occurrence.id;
      retrievingEventPromise = connector.gapi.client.calendar.events.instances(options);
    } else if (isRemoteEvent) {
      options.eventId = event.remoteId;
      retrievingEventPromise = connector.gapi.client.calendar.events.get(options);
    } else {
      retrievingEventPromise = Promise.resolve(null);
    }
  } else {
    retrievingEventPromise = Promise.resolve(null);
  }

  return retrievingEventPromise
    .then(data => {
      const remoteConnectorEventResult = data && data.result;
      let remoteConnectorEvent = null;
      if (remoteConnectorEventResult) {
        if (remoteConnectorEventResult.items) {
          remoteConnectorEvent = remoteConnectorEventResult.items.length && remoteConnectorEventResult.items[0];
        } else if (remoteConnectorEventResult.id) {
          remoteConnectorEvent = remoteConnectorEventResult;
        }
      }
      const pushMethod = isDeleteEvent ?
        connector.gapi.client.calendar.events.delete
        :remoteConnectorEvent ?
          connector.gapi.client.calendar.events.patch:
          connector.gapi.client.calendar.events.insert;

      const options = {
        calendarId: 'primary',
      };

      if (isDeleteEvent) {
        if (!remoteConnectorEvent || remoteConnectorEvent.status === 'cancelled') {
          return null;
        }
        options.eventId = remoteConnectorEvent.id;
      } else {
        if (remoteConnectorEvent) {
          options.eventId = remoteConnectorEvent.id;
          connectorEvent.id = options.eventId;
          if (isExceptionalOccurrence) {
            connectorEvent.originalStartTime = remoteConnectorEvent.originalStartTime;
            connectorEvent.recurringEventId = remoteConnectorEvent.recurringEventId;
          }
        }
        options.resource = connectorEvent;
      }

      return pushMethod(options);
    })
    .then(resp => resp && resp.result);
}

/**
 * Build event to push into Google
 * 
 * @param {Object}
 *          event Agenda Event object
 * @param {String}
 *          connectorRecurringEventId Connector parent recurrent event
 *          Identifier
 * @returns {void}
 */
function buildConnectorEvent(event, connectorRecurringEventId) {
  const connectorEvent = {};
  if (event.recurrence) {
    connectorEvent.recurrence = [`RRULE:${event.recurrence.rrule}`];
  }
  if(connectorRecurringEventId) {
    connectorEvent.recurringEventId = connectorRecurringEventId;
    if(event.allDay) {
      connectorEvent.originalStartTime = {
        date: event.occurrence.id,
        timeZone: event.timeZoneId
      };
    } else {
      connectorEvent.originalStartTime = {
        dateTime: event.occurrence.id,
        timeZone: event.timeZoneId
      };
    }
  }
  connectorEvent.status = event.status.toLowerCase();

  if(event.allDay) {
    connectorEvent.start = {
      date: event.start,
    };
  } else {
    connectorEvent.start = {
      dateTime: event.start,
      timeZone: event.timeZoneId
    };
  }
  if(event.allDay) {
    const endDate = new Date(event.end);
    endDate.setDate(endDate.getDate() +1);
    const formattedEndDate = `${endDate.getFullYear()  }-${
      pad(endDate.getMonth() + 1)  }-${
      pad(endDate.getDate())}`;
    connectorEvent.end = {
      date: formattedEndDate
    };
  } else {
    connectorEvent.end = {
      dateTime: event.end,
      timeZone: event.timeZoneId,
    };
  }
  connectorEvent.description = event.description;
  connectorEvent.summary = event.summary;
  connectorEvent.location = event.location || (event.conferences && event.conferences.length && event.conferences[0].url) || '';
  connectorEvent.source =   {
    "url": `${window.location.origin}${eXo.env.portal.context}/${eXo.env.portal.portalName}/agenda?eventId=${event.id}`,
  };
  return connectorEvent;
}

function pad(n) {
  return n < 10 && `0${n}` || n;
}