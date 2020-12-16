export default {
  name: 'agenda.officeCalendar',
  avatar: '/agenda/skin/images/office365.png',
  SCOPES: 'https://graph.microsoft.com/Calendars.Read',
  config: {
    auth: {
      clientId: null,
      authority: 'https://login.microsoftonline.com/common/',
      redirectUri: 'http://localhost:8080/',
      postLogoutRedirectUri: 'http://localhost:8080/',
    },
    cache: {
      cacheLocation: 'sessionStorage', // This configures where your cache will be stored
      storeAuthStateInCookie: true, // Set this to 'true' if you are having issues on IE11 or Edge
    }
  },
  graphConfig: {
    graphMeEndpoint: 'https://graph.microsoft.com/v1.0/me',
    graphCalendarEventsEndpoint: 'https://graph.microsoft.com/v1.0/me/calendar/calendarView?',
    eventsEndpoint: 'https://graph.microsoft.com/v1.0/me/events'
  },
  loginRequest: {
    scopes: ['Calendars.Read'],
    redirectUri: window.location.origin,
  },
  readEventsRequest: {
    scopes: ['calendars.read']
  },
  writeEventsRequest: {
    scopes: ['Calendars.ReadWrite']
  },
  canConnect: true,
  canPush: true,
  initialized: false,
  isSignedIn: false,
  pushing: false,
  accessToken: '',
  init(connectionStatusChangedCallback, loadingCallback, apiKey) {
    if (!apiKey) {
      throw new Error('Office connector can\'t be enabled with empty Client API Key.');
    }
    this.config.auth.clientId = apiKey;

    // Already initialized
    if (this.initialized) {
      return;
    }
    this.initialized = true;
    this.connectionStatusChangedCallback = connectionStatusChangedCallback;
    this.loadingCallback = loadingCallback;

    // Called when the signed in status changes, to update the UI
    // appropriately. After a sign-in, the API is called.
    initOfficeConnector(this);
  },
  connect() {
    this.loadingCallback(this, true);
    return this.officeApi.loginPopup(this.loginRequest)
      .then(loginResponse => {
        return this.getTokenPopup(loginResponse)
          .then(token => token.account.username);
      }).catch(error => {
        this.loadingCallback(this, false);
        console.error(error);
      }).finally(() => {
        this.loadingCallback(this, false);
      });
  },

  disconnect() {
    return new Promise((resolve) => {
      this.officeApi.browserStorage.removeAllAccounts();
      resolve('disconnect from office 365 done');
    });
  },
  getTokenPopup(request) {
    return this.officeApi.acquireTokenSilent(request)
      .catch(error => {
        console.warn(error);
        //silent token acquisition fails. acquiring token using popup
        // fallback to interaction when silent call fails
        return this.officeApi.acquireTokenPopup(request)
          .then(tokenResponse => {
            this.loadingCallback(this, false);
            return tokenResponse;
          }).catch(error => {
            this.loadingCallback(this, false);
            console.error(error);
          });
      }).then(token => {
        this.loadingCallback(this, false);
        return token;
      });
  },
  getEvents(periodStartDate, periodEndDate) {
    if (this.officeApi) {
      this.loadingCallback(this, true);
      return new Promise((resolve, reject) => {
        retrieveEvents(this, periodStartDate, periodEndDate)
          .then(oEvents => resolve(oEvents))
          .catch(e => {
            this.loadingCallback(this, false);
            reject(e);
          });
      });
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
    if (this.officeApi) {
      const currentUser = this.officeApi.getAllAccounts()[0];
      this.pushing = true;
      return new Promise((resolve, reject) => {
        this.writeEventsRequest.account = currentUser;

        return this.getTokenPopup(this.writeEventsRequest).then(calendarToken => {
          //const bearer = `Bearer ${calendarToken}`;
          /*retrievingEventPromise = fetch(this.graphConfig.eventsEndpoint, {
            method: 'POST',
            credentials: 'include',
            Authorization: bearer,
            headers: {
              'Content-Type': 'application/json',
            },
            body: JSON.stringify(connectorEvent),
          });
        });*/
          return pushEventToOffice(this, event, connectorRecurringEventId, deleteEvent, calendarToken)
            .then(oEvent => {
              resolve(oEvent);
            })
            .catch(error => reject(error));
        }).finally(() => this.pushing = false);
      });
    }
    return Promise.reject(new Error('Not connected'));
  },
};


/**
 * Load Office 365 Connector API javascript and prepare user authentication and
 * authorization process
 *
 * @param {Object}
 *          connector Office 365 Connector SPI
 * @returns {void}
 */
function initOfficeConnector(connector) {
  // Called when the signed in status changes, to update the UI
  // appropriately. After a sign-in, the API is called.
  connector.loadingCallback(connector, true);
  window.require(['https://alcdn.msauth.net/browser/2.8.0/js/msal-browser.min.js'], (msal) => {
    const officeApi = new msal.PublicClientApplication(connector.config);
    connector.officeApi = officeApi;

    if (officeApi.getAllAccounts().length > 0) {
      const currentUser = connector.officeApi.getAllAccounts()[0];
      if(currentUser) {
        connector.isSignedIn = true;
        connector.connectionStatusChangedCallback(connector, {
          user: currentUser.username,
          id: currentUser.localAccountId,
        });
      }
    }
    connector.loadingCallback(connector, false);
  }, (error) => {
    connector.canConnect = false;
    connector.loadingCallback(connector, false);
    console.error('Error retrieving Office API Javascript', error);
  });
}


/**
 * @param {Object}
 *          connector MS Office Connector SPI
 * @param {Date}
 *          periodStartDate Start date of period of events to retrieve
 * @param {Date}
 *          periodEndDate End date of period of events to retrieve

 * @returns {Promise} a promise with list of MS Office events
 */
function retrieveEvents(connector, periodStartDate, periodEndDate) {
  const formData = new FormData();
  formData.append('startDateTime', periodStartDate);
  formData.append('endDateTime', periodEndDate);
  const params = new URLSearchParams(formData).toString();
  const currentUser = connector.officeApi.getAllAccounts()[0];
  connector.readEventsRequest.account = currentUser;

  return connector.getTokenPopup(connector.readEventsRequest).then(calendarToken => {

    return callOfficeApi(`${connector.graphConfig.graphCalendarEventsEndpoint}${params}`,
      calendarToken.accessToken,
      (response) => {
        const events = response.value;
        events.forEach(event => {
          event.allDay = !!event.isAllDay;
          const startDate = event.start.dateTime && new Date(`${event.start.dateTime}Z`);
          const endDate = event.end.dateTime && new Date(`${event.end.dateTime}Z`);
          event.start = startDate || event.start.date;
          // Office api returns all day event with one day added for end date.
          const endDateAllDay = new Date(event.end.date);
          endDate.setDate(endDate.getDate()-1);
          event.end = event.allDay ? endDateAllDay : endDate;
          event.summary = event.subject;
          event.location = event.location.displayName;
          event.type = 'remoteEvent';
          event.color = '#FFFFFF';
        });
        connector.loadingCallback(connector, false);
        return events;
      });
  });
}

function callOfficeApi(endpoint, token, callback) {
  const headers = new Headers();
  const bearer = `Bearer ${token}`;

  headers.append('Authorization', bearer);
  headers.append('Content-Type', 'application/json');

  const options = {
    method: 'GET',
    headers: headers,
  };

  console.log('request made to Graph API at: ', new Date().toString());

  return fetch(endpoint, options)
    .then(response => response.json())
    .then(response => callback(response, endpoint))
    .catch(error => console.log(error));
}

function callOfficeApiRest(endpoint, token, callback, event) {
  const headers = new Headers();
  const bearer = `Bearer ${token}`;

  headers.append('Authorization', bearer);
  headers.append('Content-Type', 'application/json');

  const options = {
    method: 'POST',
    headers: headers,
    body: JSON.stringify(event)
  };

  console.log('request made to Graph API at: ', new Date().toString());

  return fetch(endpoint, options)
    .then(response => response.json())
    .then(response => callback(response, endpoint))
    .catch(error => console.log(error));
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
 * @param {String}
 *          calendarToken calendarToken
 * @returns {void}
 */
function pushEventToOffice(connector, event, connectorRecurringEventId, deleteEvent, calendarToken) {
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
      const endPoint = isDeleteEvent ?
        connector.gapi.client.calendar.events.delete
        :remoteConnectorEvent ?
          connector.gapi.client.calendar.events.patch:
          connector.graphConfig.eventsEndpoint;

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

      return callOfficeApiRest(endPoint,calendarToken.accessToken,(events) => {console.log(events);} ,connectorEvent);
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
  /*if (event.recurrence) {
    connectorEvent.recurrence = [`RRULE:${event.recurrence.rrule}`];
  }*/
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
  //connectorEvent.status = event.status.toLowerCase();

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
    event.isAllDay = event.allDay;
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
  //connectorEvent.description = event.description;
  connectorEvent.subject = event.summary;
  //connectorEvent.allowNewTimeProposals = false;
  /*connectorEvent.location = {
    displayName: event.location || (event.conferences && event.conferences.length && event.conferences[0].url) || ''
  };*/
  return connectorEvent;
}

function pad(n) {
  return n < 10 && `0${n}` || n;
}

