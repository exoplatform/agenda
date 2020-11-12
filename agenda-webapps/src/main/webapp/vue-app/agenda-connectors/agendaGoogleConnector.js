export default {
  name: 'agenda.googleCalendar',
  avatar: '/agenda/skin/images/Google.png',
  CLIENT_ID: '694838797844-h0q657all0v8cq66p9nume6mti6cll4o.apps.googleusercontent.com',
  DISCOVERY_DOCS: ['https://www.googleapis.com/discovery/v1/apis/calendar/v3/rest'],
  SCOPES: 'https://www.googleapis.com/auth/calendar.readonly',
  initialized: false,
  isSignedIn: false,
  currentUser: {},
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
          self_.currentUser = self_.gapi.auth2.getAuthInstance().currentUser.get();
          connectionStatusChangedCallback(self_, {
            user: self_.currentUser.getBasicProfile().getEmail(),
            id: self_.currentUser.getId(),
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
  },
  getEvents(periodStartDate, periodEndDate) {
    const self_ = this;
    return this.gapi.client.calendar.events.list({
      'calendarId': 'primary',
      'timeMin': periodStartDate,
      'timeMax': periodEndDate,
      'singleEvents': true,
      'orderBy': 'startTime'
    }).then(events => events.result.items).then(events => {
      events.forEach(event => {
        event.calendar = {
          owner: {
            profile: {
              avatarUrl: self_.avatar,
              displayName: self_.currentUser.getBasicProfile().getEmail(),
            }
          }
        };
        if(event.attendees) {
          event.attendees.forEach(attendee => {
            attendee.response = attendee.responseStatus === 'needsAction' ? 'NEEDS_ACTION' : attendee.responseStatus.toUpperCase();
            attendee.identity = {
              providerId: 'organization',
              profile: {
                avatar: attendee.avatar ? attendee.avatar : '/portal/rest/v1/social/users/default-image/avatar',
                fullname: attendee.email,
              }
            };
          });
        }

        event.start = event.start.dateTime;
        event.end = event.end.dateTime;
        event.name = event.summary;
        event.type = 'remoteEvent';
      });
      return events;
    });
  }
};