export default {
  // Configuration object constructed.
  config: {
    auth: {
      clientId: 'fd78b3a2-b6a0-465e-a5ea-d94fa9cb3a95',
      authority: 'https://login.microsoftonline.com/common/',
      redirectUri: 'http://localhost:8080/',
    },
    cache: {
      cacheLocation: 'sessionStorage', // This configures where your cache will be stored
      storeAuthStateInCookie: false, // Set this to 'true' if you are having issues on IE11 or Edge
    }
  },
  graphConfig: {
    graphMeEndpoint: 'https://graph.microsoft.com/v1.0/me',
    graphMailEndpoint: 'https://graph.microsoft.com/v1.0/me/messages'
  },
  loginRequest: {
    scopes: ['Calendars.Read'],
    redirectUri: 'http://localhost:8080/',
  },
  /*logoutRequest: {
    account: this.myMSALObj.getAccountByUsername(this.username)
  },*/
  accessTokenRequest: {
    scopes: ['calendars.read']
  },
  username: '',
  //officeApi: new PublicClientApplication(config),
  // create UserAgentApplication instance
  //userAgentApplication: new UserAgentApplication(config),
  name: 'agenda.officeCalendar',
  avatar: '/agenda/skin/images/office365.png',
  CLIENT_ID: 'fd78b3a2-b6a0-465e-a5ea-d94fa9cb3a95',
  SCOPES: 'https://graph.microsoft.com/Calendars.Read',
  canConnect: true,
  canPush: false,
  initialized: false,
  isSignedIn: false,
  pushing: false,
  accessToken: '',
  init(connectionStatusChangedCallback, loadingCallback) {
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
    return this.myMSALObj.loginPopup(this.loginRequest)
      .then(loginResponse => {
        console.log(`id_token acquired at: ${  new Date().toString()}`);
        console.log(loginResponse);
        return this.getTokenPopup(loginResponse);
      }).catch(error => {
        this.loadingCallback(this, false);
        console.error(error);
      }).finally(() => {
        this.loadingCallback(this, false);
      });
  },

  disconnect() {
    this.myMSALObj.logout();
  },
  getTokenPopup(request) {
    return this.myMSALObj.acquireTokenSilent(request)
      .catch(error => {
        console.log(error);
        //silent token acquisition fails. acquiring token using popup
        this.loadingCallback(this, false);
        // fallback to interaction when silent call fails
        return this.myMSALObj.acquireTokenPopup(request)
          .then(tokenResponse => {
            console.log(`tokenResponse=========> ${tokenResponse}`);
            return tokenResponse;
          }).catch(error => {
            this.loadingCallback(this, false);
            console.error(error);
          });
      }).then(token => {
        return token.account.username;
      });
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
function initOfficeConnector(connector) {
  // Called when the signed in status changes, to update the UI
  // appropriately. After a sign-in, the API is called.
  connector.loadingCallback(connector, true);
  window.require(['https://alcdn.msauth.net/browser/2.8.0/js/msal-browser.min.js'], (msal) => {
    //window.require(['https://alcdn.msauth.net/lib/1.4.0/js/msal.js'], (Msal) => {

    const myMSALObj = new msal.PublicClientApplication(connector.config);
    connector.myMSALObj = myMSALObj;

    if (myMSALObj.getAllAccounts().length > 0) {
      const currentUser = connector.myMSALObj.getAllAccounts()[0];
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
  //});
}
/*

function callMSGraph(endpoint, token, callback) {
  const headers = new Headers();
  const bearer = `Bearer ${token}`;

  headers.append('Authorization', bearer);

  const options = {
    method: 'GET',
    headers: headers
  };

  console.log('request made to Graph API at: ', new Date().toString());

  fetch(endpoint, options)
    .then(response => response.json())
    .then(response => callback(response, endpoint))
    .catch(error => console.log(error));
}
function updateUI(data, endpoint) {
  console.log('Graph API responded at: ', new Date().toString());
  console.warn('update UI: \t',data, endpoint);
}*/
