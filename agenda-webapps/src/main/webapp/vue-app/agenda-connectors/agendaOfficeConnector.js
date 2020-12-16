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
      storeAuthStateInCookie: false, // Set this to 'true' if you are having issues on IE11 or Edge
    }
  },
  graphConfig: {
    graphMeEndpoint: 'https://graph.microsoft.com/v1.0/me',
    graphCalendarEventsEndpoint: 'https://graph.microsoft.com/v1.0/me/calendar/calendarView?'
  },
  loginRequest: {
    scopes: ['Calendars.Read'],
    redirectUri: window.location.origin,
  },
  CalendarRequest: {
    scopes: ['calendars.read']
  },
  canConnect: true,
  canPush: false,
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
        return this.getTokenPopup(loginResponse).then(token => token.account.username);
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
      //const currentUser = this.gapi.auth2.getAuthInstance().currentUser.get();

      this.loadingCallback(this, true);
      return new Promise((resolve, reject) => {
        //if (currentUser.hasGrantedScopes(this.SCOPE_READ)) {
        retrieveEvents(this, periodStartDate, periodEndDate)
          .then(oEvents => resolve(oEvents))
          .catch(e => {
            this.loadingCallback(this, false);
            reject(e);
          });
        /*} else {
          currentUser.grant({
            scope: this.SCOPE_READ
          }).then(
            () => retrieveEvents(this, periodStartDate, periodEndDate)
              .then(gEvents => resolve(gEvents))
              .catch(e => {
                this.loadingCallback(this, false);
                reject(e);
              })
            ,(error) => reject(error)
          );
        }*/
      });
    } else {
      return Promise.resolve(null);
    }
  },
};


/**
 * Load Office Connector API javascript and prepare user authentication and
 * authorization process
 *
 * @param {Object}
 *          connector Office Connector SPI
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
  //const currentUser = connector.officeApi.getAllAccounts()[0];

  const formData = new FormData();
  formData.append('startDateTime', periodStartDate);
  formData.append('endDateTime', periodEndDate);
  const params = new URLSearchParams(formData).toString();

  return connector.getTokenPopup(connector.CalendarRequest).then(calendarToken => {
    return callOfficeApi(`${connector.graphConfig.graphCalendarEventsEndpoint}${params}`,
      calendarToken.accessToken,
      (response) => {
        const events = response.value;
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
        connector.loadingCallback(connector, false);
        return events;
      });
  });
}

function callOfficeApi(endpoint, token, callback) {
  const headers = new Headers();
  const bearer = `Bearer ${token}`;

  headers.append('Authorization', bearer);

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

