import {pad} from '../agenda-common/js/AgendaUtils';

export default {
  name: 'agenda.googleCalendar',
  description: 'agenda.googleCalendar.description',
  avatar: '/agenda/skin/images/Google.png',
  CLIENT_ID: '694838797844-h0q657all0v8cq66p9nume6mti6cll4o.apps.googleusercontent.com',
  DISCOVERY_DOCS: ['https://www.googleapis.com/discovery/v1/apis/calendar/v3/rest'],
  SCOPE_READONLY: 'https://www.googleapis.com/auth/calendar.events.readonly',
  SCOPE_WRITE: 'https://www.googleapis.com/auth/calendar.events',
  canConnect: true,
  initialized: false,
  isSignedIn: false,
  hasSynchronizedEvent: false,
  init(connectionStatusChangedCallback, loadingCallback) {
    // Already initialized
    if (this.initialized) {
      return;
    }
    this.initialized = true;
    this.connectionStatusChangedCallback = connectionStatusChangedCallback;
    this.loadingCallback = loadingCallback;

    initGoogleConnector(this);
  },
  connect() {
    this.loadingCallback(this, true);
    // Return a Promise with connected username
    return this.gapi.auth2.getAuthInstance().signIn()
      .then(connectedUser => connectedUser.getBasicProfile().getEmail());
  },
  disconnect() {
    this.loadingCallback(this, true);
    return this.gapi.auth2.getAuthInstance().signOut();
  },
  getEvents(periodStartDate, periodEndDate) {
    if (this.gapi && this.gapi.client && this.gapi.client.calendar) {
      this.loadingCallback(this, true);
      return this.gapi.client.calendar.events.list({
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
        this.loadingCallback(this, false);
        return events;
      }).catch(e => {
        this.loadingCallback(this, false);
        throw new Error(e);
      });
    } else {
      return Promise.resolve(null);
    }
  },
  synchronizeEvent(event, connectorRecurringEventId) {
    if (this.gapi && this.gapi.auth2.getAuthInstance()) {
      const currentUser = this.gapi.auth2.getAuthInstance().currentUser.get();

      this.hasSynchronizedEvent = false;
      return new Promise((resolve, reject) => {
        return currentUser.grant({
          scope: this.SCOPE_WRITE
        }).then(
          () => pushEventToGoogle(this, event, connectorRecurringEventId)
            .then(gEvent => {
              this.hasSynchronizedEvent = true;
              resolve(gEvent);
            })
            .catch(error => {
              this.hasSynchronizedEvent = false;
              reject(error);
            })
          ,
          (error) => {
            this.hasSynchronizedEvent = false;
            reject(error);
          })
          .catch(error => {
            this.hasSynchronizedEvent = false;
            reject(error);
          });
      });
    }
    return Promise.reject(new Error('Not connected'));
  },
};

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
        connector.connectionStatusChangedCallback(connector, {
          user: currentUser.getBasicProfile().getEmail(),
          id: currentUser.getId(),
        });
      } else {
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
        scope: connector.SCOPE_READONLY,
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
 * @returns {void}
 */
function pushEventToGoogle(connector, event, connectorRecurringEventId) {
  const eventToSynchronize = buildEventToSynchronize(event, connectorRecurringEventId);
  let retrievingEventPromise = null;
  if (event.remoteId && event.remoteProviderId === connector.technicalId) {
    retrievingEventPromise = connector.gapi.client.calendar.events.get({
      'calendarId': 'primary',
      'eventId': event.remoteId,
    });
  } else {
    retrievingEventPromise = Promise.resolve(null);
  }
  return retrievingEventPromise
    .then(remoteEvent => {
      const updateRemoteEvent = remoteEvent && remoteEvent.result && remoteEvent.result.status;
      const pushMethod = updateRemoteEvent ?
        connector.gapi.client.calendar.events.update:
        connector.gapi.client.calendar.events.insert;

      const options = {
        'calendarId': 'primary',
        'resource': eventToSynchronize
      };

      if (updateRemoteEvent) {
        options.eventId = event.remoteId;
      }

      return pushMethod(options);
    })
    .then(resp => {
      if (resp && resp.result) {
        const synchronizedEvent = resp.result;
        synchronizedEvent.agendaId = event.id;
        return synchronizedEvent;
      }
    });
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
function buildEventToSynchronize(event, connectorRecurringEventId) {
  const eventToSynchronize = {};
  if (event.recurrence) {
    eventToSynchronize.recurrence = [`RRULE:${event.recurrence.rrule}`];
  }
  if(connectorRecurringEventId) {
    eventToSynchronize.recurringEventId = connectorRecurringEventId;
    eventToSynchronize.originalStartTime = {
      dateTime: event.occurrence.id,
      timeZone: event.timeZoneId
    };
  }
  if(event.allDay) {
    eventToSynchronize.start = {
      date: event.start,
    };
  } else {
    eventToSynchronize.start = {
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
    eventToSynchronize.end = {
      date: formattedEndDate
    };
  } else {
    eventToSynchronize.end = {
      dateTime: event.end,
      timeZone: event.timeZoneId
    };
  }
  eventToSynchronize.description = event.description;
  eventToSynchronize.summary = event.summary;
  eventToSynchronize.location = event.location;
  return eventToSynchronize;
}