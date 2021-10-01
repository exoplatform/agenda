// SPDX-FileCopyrightText: 2021 eXo Platform SAS
//
// SPDX-License-Identifier: AGPL-3.0-only

export default {
  name: 'agenda.officeCalendar',
  avatar: '/agenda/skin/images/office365.png',
  config: {
    auth: {
      clientId: null,
      authority: 'https://login.microsoftonline.com/common/',
      redirectUri: window.location.origin,
      postLogoutRedirectUri: window.location.origin,
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
  CALENDAR_READ_SCOPE: ['Calendars.Read'],
  CALENDAR_WRITE_SCOPE: ['Calendars.Read', 'Calendars.ReadWrite'],
  loginRequest: {
    redirectUri: window.location.origin,
  },
  calendarRequest: {
    redirectUri: window.location.origin,
  },
  canConnect: true,
  canPush: false,
  initialized: false,
  isSignedIn: false,
  pushing: false,
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
  connect(askWriteAccess) {
    if (this.isSignedIn && this.user && (!askWriteAccess || this.canPush)) {
      return Promise.resolve(this.user);
    }

    if (askWriteAccess) {
      this.calendarRequest.scopes = this.CALENDAR_WRITE_SCOPE;
      this.loginRequest.scopes = this.CALENDAR_WRITE_SCOPE;
    } else if (!this.calendarRequest.scopes) {
      this.calendarRequest.scopes = this.CALENDAR_READ_SCOPE;
      this.loginRequest.scopes = this.CALENDAR_READ_SCOPE;
    }

    this.loadingCallback(this, true);
    return this.officeApi.loginPopup(this.loginRequest)
      .then(loginResponse => getTokenPopup(this, loginResponse))
      .then(tokenObject => {
        const username = tokenObject && tokenObject.account && tokenObject.account.username;
        this.user = username;
        this.canPush = askWriteAccess;

        this.connectionStatusChangedCallback(this, {
          user: username,
        });

        return username;
      })
      .catch((error) => {
        this.connectionStatusChangedCallback(this, false);
        throw error;
      })
      .finally(() => this.loadingCallback(this, false));
  },
  disconnect() {
    return new Promise((resolve) => {
      this.officeApi.browserStorage.removeAllAccounts();
      resolve('disconnect from office 365 done');
    });
  },
  getEvents(periodStartDate, periodEndDate) {
    if (this.officeApi) {
      this.loadingCallback(this, true);
      return retrieveEvents(this, periodStartDate, periodEndDate)
        .finally(() => this.loadingCallback(this, false));
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
      if (this.canPush) {
        this.pushing = true;
        return getTokenPopup(this, this.calendarRequest)
          .then(token => pushEventToOffice(this, event, connectorRecurringEventId, deleteEvent, token))
          .finally(() => this.pushing = false);
      } else {
        return this.connect(true).then(() => {
          this.pushing = true;
          return getTokenPopup(this, this.calendarRequest)
            .then(token => pushEventToOffice(this, event, connectorRecurringEventId, deleteEvent, token))
            .finally(() => this.pushing = false);
        });
      }
    } else {
      return Promise.reject(new Error('Not connected'));
    }
  },
};

/**
 * Generates a token to be used to request Graph API
 * 
 * @param {Object} connector current Office connector
 * @param {Object} request User request
 * @returns {Promise} with result the token to be used in user queries
 */
function getTokenPopup(connector, request) {
  if (!request.account && connector.user) {
    request.account = connector.user;
  }
  return connector.officeApi.acquireTokenSilent(request)
    .catch(() => {
      // silent token acquisition fails. acquiring token using popup
      // fallback to interaction when silent call fails
      return connector.officeApi.acquireTokenPopup(request)
        .then(tokenResponse => {
          connector.loadingCallback(connector, false);
          return tokenResponse;
        }).catch(error => {
          connector.loadingCallback(connector, false);
          console.error(error);
        });
    }).finally(() => connector.loadingCallback(connector, false));
}

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

    const currentUser = officeApi.getAllAccounts().length > 0 && connector.officeApi.getAllAccounts()[0] || null;
    if (currentUser) {
      connector.connectionStatusChangedCallback(connector, {
        user: currentUser.username,
        id: currentUser.localAccountId,
      });

      const request = {
        redirectUri: window.location.origin,
        account: currentUser.username,
        scopes: connector.CALENDAR_WRITE_SCOPE,
      };

      return officeApi.acquireTokenSilent(request)
        .then(() => {
          connector.calendarRequest.scopes = connector.CALENDAR_WRITE_SCOPE;
          connector.loginRequest.scopes = connector.CALENDAR_WRITE_SCOPE;
          connector.canPush = true;
        })
        .catch(() => {
          request.scopes = connector.CALENDAR_READ_SCOPE;
          return officeApi.acquireTokenSilent(request)
            .then(() => {
              connector.calendarRequest.scopes = connector.CALENDAR_READ_SCOPE;
              connector.loginRequest.scopes = connector.CALENDAR_READ_SCOPE;
              connector.canPush = false;
            });
        })
        .finally(() => {
          connector.isSignedIn = true;
          connector.user = currentUser.username;
          connector.loadingCallback(connector, false);
        });
    } else {
      connector.loadingCallback(connector, false);
    }
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

  return getTokenPopup(connector, connector.calendarRequest)
    .then(tokenObject => officeApiGet(`${connector.graphConfig.graphCalendarEventsEndpoint}${params}`, tokenObject.accessToken))
    .then(data => {
      const events = data.value;
      if (!events) {
        throw new Error('Empty events list sent from server');
      }
      events.forEach(event => {
        event.allDay = !!event.isAllDay;
        const StartDate = event.start.dateTime && new Date(`${event.start.dateTime}Z`);
        const EndDate = event.end.dateTime && new Date(`${event.end.dateTime}Z`);
        event.start = StartDate || event.start.date;
        event.end = EndDate  || event.start.date;
        event.summary = event.subject;
        event.location = event.location.displayName;
        event.type = 'remoteEvent';
        event.color = '#FFFFFF';
      });
      return events;
    });
}

function officeApiGetInstance(endpoint, token, date) {
  const headers = new Headers();
  const bearer = `Bearer ${token}`;

  headers.append('Authorization', bearer);

  const options = {
    method: 'GET',
    headers: headers,
  };

  const dateTime = new Date(date).getTime();
  const startDateTime = `${new Date(dateTime - 3600000).toISOString().substring(0, 10)}T00:00:00Z`;
  const endDateTime = `${new Date(dateTime + 3600000).toISOString().substring(0, 10)}T23:59:59Z`;

  return fetch(`${endpoint}?startDateTime=${startDateTime}&endDateTime=${endDateTime}`, options)
    .then(resp => {
      if (resp && resp.ok) {
        return resp.json();
      } else {
        throw new Error('Server indicates an error while sending request');
      }
    })
    .then(data => {
      if (data && data.error) {
        throw new Error(`Server indicates an error while sending request: ${data.error}`);
      }
      return data;
    });
}

function officeApiGet(endpoint, token) {
  const headers = new Headers();
  const bearer = `Bearer ${token}`;
  
  headers.append('Authorization', bearer);
  
  const options = {
    method: 'GET',
    headers: headers,
  };
  
  return fetch(endpoint, options)
    .then(resp => {
      if (resp && resp.ok) {
        return resp.json();
      } else {
        throw new Error('Server indicates an error while sending request');
      }
    })
    .then(data => {
      if (data && data.error) {
        throw new Error(`Server indicates an error while sending request: ${data.error}`);
      }
      return data;
    });
}

function officeApiDelete(endpoint, token) {
  const headers = new Headers();
  const bearer = `Bearer ${token}`;

  headers.append('Authorization', bearer);

  const options = {
    method: 'DELETE',
    headers: headers,
  };

  return fetch(endpoint, options)
    .then(resp => {
      if (!resp || !resp.ok) {
        throw new Error('Server indicates an error while sending request');
      }
    });
}

function officeApiUpdate(endpoint, token, event) {
  const headers = new Headers();
  const bearer = `Bearer ${token}`;
  
  headers.append('Authorization', bearer);
  headers.append('Content-Type', 'application/json');
  
  const options = {
    method: 'PATCH',
    headers: headers,
    body: JSON.stringify(event)
  };
  
  return fetch(endpoint, options)
    .then(resp => {
      if (resp && resp.ok) {
        return resp.json();
      } else {
        throw new Error('Server indicates an error while sending request');
      }
    })
    .then(data => {
      if (data && data.error) {
        throw new Error(`Server indicates an error while sending request: ${data.error}`);
      }
      return data;
    });
}

function officeApiPost(endpoint, token, event) {
  const headers = new Headers();
  const bearer = `Bearer ${token}`;
  
  headers.append('Authorization', bearer);
  headers.append('Content-Type', 'application/json');
  
  const options = {
    method: 'POST',
    headers: headers,
    body: JSON.stringify(event)
  };
  
  return fetch(endpoint, options)
    .then(resp => {
      if (resp && resp.ok) {
        return resp.json();
      } else {
        throw new Error('Server indicates an error while sending request');
      }
    })
    .then(data => {
      if (data && data.error) {
        throw new Error(`Server indicates an error while sending request: ${data.error}`);
      }
      return data;
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
    if (isExceptionalOccurrence) {
      const getEventEndpointInstances = `${connector.graphConfig.eventsEndpoint}/${connectorRecurringEventId}/instances`;
      retrievingEventPromise = getTokenPopup(connector, connector.calendarRequest)
        .then(tokenObject => officeApiGetInstance(getEventEndpointInstances, tokenObject.accessToken, event.occurrence.id))
        .catch(() => null);
    } else if (isRemoteEvent) {
      const getEventEndpoint = `${connector.graphConfig.eventsEndpoint}/${event.remoteId}`;
      retrievingEventPromise = getTokenPopup(connector, connector.calendarRequest)
        .then(tokenObject => officeApiGet(getEventEndpoint, tokenObject.accessToken))
        .catch(() => null);
    } else {
      retrievingEventPromise = Promise.resolve(null);
    }
  } else {
    retrievingEventPromise = Promise.resolve(null);
  }

  return retrievingEventPromise
    .then(remoteConnectorEventResult => {
      let remoteConnectorEvent = null;
      if (remoteConnectorEventResult) {
        if (remoteConnectorEventResult.value && remoteConnectorEventResult.value.length) {
          remoteConnectorEvent = remoteConnectorEventResult.value[0];
        } else if (remoteConnectorEventResult.id) {
          remoteConnectorEvent = remoteConnectorEventResult;
        }
      }
      const updatePoint = isDeleteEvent ?
        officeApiDelete
        :remoteConnectorEvent ?
          officeApiUpdate:
          officeApiPost;

      let endPoint = connector.graphConfig.eventsEndpoint;

      if (isDeleteEvent) {
        if (!remoteConnectorEvent || remoteConnectorEvent.status === 'cancelled') {
          return null;
        }
        endPoint = `${endPoint}/${remoteConnectorEvent.id}`;
      } else if (remoteConnectorEvent) {
        endPoint = `${endPoint}/${remoteConnectorEvent.id}`;

        connectorEvent.id = remoteConnectorEvent.id;
      }

      return updatePoint(endPoint, calendarToken.accessToken, connectorEvent);
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
function buildConnectorEvent(event) {
  const connectorEvent = {
    subject: event.summary,
    body: {
      contentType: 'HTML',
      content: event.description || '',
    },
    isCancelled: event.status === 'CANCELLED',
    isAllDay: !!event.allDay,
    location: {
      displayName: event.location || '',
    },
  };

  if (event.recurrence) {
    let recurrenceType = event.recurrence.frequency.toLowerCase();
    if (recurrenceType === 'monthly') {
      recurrenceType = 'absoluteMonthly';
    }
    if (recurrenceType === 'yearly') {
      recurrenceType = 'absoluteYearly';
    }

    let daysOfWeek = event.recurrence.byDay;
    if (daysOfWeek) {
      daysOfWeek = daysOfWeek.map(day => {
        switch (day) {
        case 'MO': return 'monday';
        case 'TU': return 'tuesday';
        case 'WE': return 'wednesday';
        case 'TH': return 'thursday';
        case 'FR': return 'friday';
        case 'SA': return 'saturday';
        case 'SU': return 'sunday';
        }
      });
    }
    const daysOfMonth = event.recurrence.byMonthDay;
    const rangeType = event.recurrence.until ? 'endDate' : event.recurrence.count ? 'numbered' : 'noEnd';
    const untilDate = event.recurrence.until ? event.recurrence.until.substring(0, 10) : null;

    connectorEvent.recurrence = {
      pattern: {
        interval: event.recurrence.interval,
        type: recurrenceType,
      },
      range: {
        type: rangeType,
        startDate: event.start.substring(0, 10),
      },
    };
    if (daysOfWeek) {
      connectorEvent.recurrence.pattern.daysOfWeek = daysOfWeek;
      connectorEvent.recurrence.pattern.firstDayOfWeek = 'monday';
    }
    if (daysOfMonth) {
      connectorEvent.recurrence.pattern.daysOfMonth = daysOfMonth;
    }
    if (event.recurrence.count) {
      connectorEvent.recurrence.range.numberOfOccurrences = event.recurrence.count;
    }
    if (untilDate) {
      connectorEvent.recurrence.range.endDate = untilDate;
    }
  }

  if (event.allDay) {
    connectorEvent.start = {
      dateTime: event.start.substring(0, 10),
      timeZone: event.timeZoneId,
    };
    connectorEvent.end = {
      dateTime: event.end.substring(0, 10),
      timeZone: event.timeZoneId,
    };
  } else {
    connectorEvent.start = {
      dateTime: event.start.substring(0, 19),
      timeZone: event.timeZoneId,
    };
    connectorEvent.end = {
      dateTime: event.end.substring(0, 19),
      timeZone: event.timeZoneId,
    };
  }

  return connectorEvent;
}