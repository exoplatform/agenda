export default {
  name: 'agenda.googleCalendar',
  avatar: '/agenda/skin/images/Google.png',
  CLIENT_ID: '899406858008-inaa3tjhhn3blslb7pvlsi7nvmd08l2f.apps.googleusercontent.com',
  DISCOVERY_DOCS: ['https://www.googleapis.com/discovery/v1/apis/calendar/v3/rest'],
  SCOPES: 'https://www.googleapis.com/auth/calendar.readonly',
  initialized: false,
  isSignedIn: false,
  init(connectionStatusChangedCallback, loadingCallback) {
    // Already initialized
    if (this.initialized) {
      return;
    }
    this.initialized = true;
    this.connectionStatusChangedCallback = connectionStatusChangedCallback;
    this.loadingCallback = loadingCallback;

    const self_ = this;
    // Called when the signed in status changes, to update the UI
    // appropriately. After a sign-in, the API is called.
    function updateSigninStatus(isSignedIn) {
      self_.isSignedIn = isSignedIn;
      try {
        if (isSignedIn) {
          const currentUser = self_.gapi.auth2.getAuthInstance().currentUser.get();
          connectionStatusChangedCallback(self_, {
            user: currentUser.getBasicProfile().getEmail(),
            id: currentUser.getId(),
          });
        } else {
          self_.connectionStatusChangedCallback(self_, false);
        }
      } finally {
        self_.loadingCallback(self_, false);
      }
    }

    this.loadingCallback(this, true);
    window.require(['https://apis.google.com/js/api.js'], () => {
      this.gapi = gapi;
      this.gapi.load('client:auth2', function() {
        gapi.client.init({
          clientId: self_.CLIENT_ID,
          discoveryDocs: self_.DISCOVERY_DOCS,
          scope: self_.SCOPES,
        }).then(function () {
          // Listen for sign-in state changes.
          gapi.auth2.getAuthInstance().isSignedIn.listen(updateSigninStatus);

          // Handle the initial sign-in state.
          updateSigninStatus(gapi.auth2.getAuthInstance().isSignedIn.get());
        }, function(error) {
          console.error(error);
        });
      });
    });
  },
  connect() {
    this.loadingCallback(this, true);
    return this.gapi.auth2.getAuthInstance().signIn();
  },
  disconnect() {
    this.loadingCallback(this, true);
    return this.gapi.auth2.getAuthInstance().signOut();
  }
};