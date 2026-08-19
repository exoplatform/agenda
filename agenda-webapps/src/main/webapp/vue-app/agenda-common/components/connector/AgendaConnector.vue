<script>
// How long a reported copy failure keeps the next ones quiet. One user
// gesture rarely produces one copy: a recurring series pushes its parent and
// each of its exceptional occurrences separately, and answering or deleting
// several meetings in a row fires one push per meeting. When the account is
// unreachable or its password was refused, every one of those fails for the
// very same reason, and a message per failure would bury the one fact worth
// reading under a column of identical toasts. Long enough to cover the burst
// a single gesture produces, short enough that a failure the user provokes
// again later is told again.
const COPY_FAILURE_QUIET_PERIOD_MS = 10000;

export default {
  props: {
    settings: {
      type: Object,
      default: () => null,
    },
    connectors: {
      type: Object,
      default: () => null,
    },
    /**
     * Whether connecting an account should go straight on to ask where the
     * copies are written. True in the agenda, where connecting is the task
     * being carried out; false in the settings, where the copy switch asks
     * for it instead.
     */
    offerMirrorCalendar: {
      type: Boolean,
      default: true,
    },
  },
  data: () => ({
    loading: false,
    copyFailureAnnouncedAt: 0,
  }),
  computed: {
    remoteProviders() {
      return this.settings && this.settings.remoteProviders;
    },
    connectedConnectorUser() {
      return this.connectedConnector && this.connectedConnector.user;
    },
    connectedConnector() {
      return this.connectors && this.connectors.find(connector => connector.connected);
    },
  },
  watch: {
    remoteProviders() {
      if (this.remoteProviders) {
        this.refreshConnectorsList();
      }
    },
  },
  created() {
    // Retrieving list of registered connectors from extensionRegistry
    document.addEventListener('agenda-connectors-refresh', this.refreshConnectorsList);
    this.$root.$on('agenda-connectors-init', this.initConnectors);
    this.$root.$on('agenda-connector-connect', this.connect);
    this.$root.$on('agenda-connector-disconnect', this.disconnect);
    this.$root.$on('agenda-event-saved', this.pushEvent);
    this.$root.$on('agenda-event-deleted', this.deleteEvent);
    this.$root.$on('agenda-event-response-updated', this.pushEventResponse);
  },
  mounted() {
    this.refreshConnectorsList();
  },
  methods: {
    refreshConnectorsList() {
      // Get list of connectors from extensionRegistry
      const connectors = extensionRegistry.loadExtensions('agenda', 'connectors') || [];

      // Check connectors 'enablement' status from store
      if (this.remoteProviders && this.remoteProviders.length) {
        connectors
          .forEach(connector => {
            const connectorObj = this.remoteProviders.find(connectorSettings => connectorSettings.name === connector.name);
            connector.enabled = connectorObj && connectorObj.enabled || false;
            connector.apiKey = connectorObj && connectorObj.apiKey || '';
            connector.connected = connector.enabled && this.settings.connectedRemoteProvider === connectorObj.name;
            connector.user = connector.connected && this.settings.connectedRemoteUserId || '';
          });
      } else {
        connectors.forEach(connector => connector.enabled = false);
      }

      this.initConnectors();

      this.$emit('connectors-loaded', connectors);
    },
    initConnectors() {
      this.connectors
        .forEach(connector => {
          if (connector.init && !connector.initialized && connector.enabled && connector.apiKey) {
            connector.init(this.connectionStatusChanged, this.connectionLoading, connector.apiKey);
          }
        });
    },
    /**
     * Connects a connector to its remote account: disconnects any other
     * connected connector, delegates the connection to the connector itself,
     * then stores the connected user. Once the connection succeeded, when the
     * connector can create a calendar on the remote server, the mirror
     * calendar step is offered — the destination the pushed meetings will be
     * written to — so a refused creation is dealt with at connect time, not
     * at first push.
     *
     * @param {Object} connector the connector to connect
     * @returns {Promise} resolves once the connection is established and stored
     */
    connect(connector) {
      this.errorMessage = null;

      const disconnectPromises = [];
      this.connectors.forEach(otherConnector => {
        if (connector.name !== otherConnector.name && (otherConnector.user || otherConnector.isSignedIn)) {
          disconnectPromises.push(this.disconnect(otherConnector));
        }
      });

      this.$set(connector, 'loading', true);
      return Promise.all(disconnectPromises)
        .then(() => connector.connect(this.settings && this.settings.automaticPushEvents))
        .then((userId) => {
          return this.$settingsService.saveUserConnector(connector.name, userId)
            .then(() => {
              this.$set(connector, 'isSignedIn', true);
              this.$set(connector, 'user', userId);
              // Marked connected here rather than left to the settings to say
              // so. That flag is derived from the settings held by this page,
              // and the refresh asked for below only replaces them later — so
              // between connecting and that arriving, nothing counted as
              // connected and every event saved in the meantime was silently
              // not pushed. Disconnecting already clears the flag directly;
              // this is the other half of it.
              this.$set(connector, 'connected', true);
            });
        })
        .then(() => {
          this.$set(connector, 'loading', false);
          this.$root.$emit('agenda-settings-refresh');
          this.refreshConnectorsList();
          // Not offered where connecting is one preference among others: a
          // drawer taking over the page is for the agenda, where connecting
          // is the task at hand. In the settings the same step is reached by
          // turning the copy switch on, which is what asks for it.
          if (this.offerMirrorCalendar && connector.canCreateCalendar) {
            this.$root.$emit('agenda-connector-mirror-calendar-open', connector);
          }
        })
        .catch(error => {
          console.error('Connected - error', connector.name, error);

          this.$set(connector, 'loading', false);
          if (error.error !== 'popup_closed_by_user') {
            console.error('Error while connecting to remote account: ', error);
            this.errorMessage = this.$t('agenda.connectionFailure');
          }
        });
    },
    disconnect(connector) {
      //disconnect from connected browser
      if (connector.isSignedIn) {
        return connector.disconnect().then(() => this.resetConnector(connector));
      } else {//disconnect from other browser
        return this.resetConnector(connector);
      }
    },
    resetConnector(connector) {
      this.$set(connector, 'loading', true);
      return this.$settingsService.resetUserConnector()
        .then(() => {
          this.$set(connector, 'isSignedIn', false);
          this.$set(connector, 'connected', false);
          this.$set(connector, 'user', null);
          // canPush is not cleared from here, and not left alone either: what
          // it means is the connector's own business.
          //
          // Clearing it centrally turned the ability off for the rest of the
          // page's life — the connectors are the shared objects the registry
          // hands out — so disconnecting once stopped every later push and
          // only a reload brought it back. But leaving it set is wrong for the
          // connectors where it is a permission rather than an ability: for
          // OAuth accounts it records that write scope was granted, and the
          // grant is exactly what disconnecting throws away. Reconnecting then
          // saw a scope it no longer had, skipped asking for it, and the first
          // copy failed on a token the client did not hold.
          //
          // So the connector is asked to reset whatever it keeps, if it keeps
          // anything. One that reports a static ability — CalDAV can always
          // push — implements nothing and is left untouched.
          if (typeof connector.resetPushAbility === 'function') {
            connector.resetPushAbility();
          }
          this.$root.$emit('agenda-settings-refresh');
          this.refreshConnectorsList();
        })
        .finally(() => {
          this.$set(connector, 'loading', false);
        });
    },
    connectionLoading(connector, loading) {
      this.$set(connector, 'loading', loading);

      if (loading) {
        this.$set(connector, 'error', '');
        this.loading++;
      } else if (this.loading) {
        this.loading--;
      }
    },
    connectionStatusChanged(connector, connectedUser, error) {
      if (connectedUser) {
        this.$set(connector, 'error', '');
        this.$root.$emit('agenda-connector-connected', connector);
      } else if (error) {
        const errorMessage = error.details || error.error || error.message || String(error);
        this.$set(connector, 'error', errorMessage);
      } else {
        this.cleanConnectorStatus(connector, connectedUser);
      }
    },
    cleanConnectorStatus(connector, connectedUser) {
      this.$set(connector, 'error', '');

      if (this.connectedConnectorUser) {
        //if user is connected with different account from other browser
        if (connectedUser && connectedUser.user !== this.connectedConnectorUser) {
          connector.disconnect();
        }
      } else if (connector && connector.isSignedIn) {
        //if user disconnected from other browser
        connector.disconnect();
      }

      this.refreshConnectorsList();
    },
    /**
     * Whether an event lives in a space calendar, read from the calendar
     * owner's identity provider: 'space' identities own space calendars,
     * 'organization' identities own personal ones. The providerId travels in
     * every payload reaching the push handlers — server-built events carry
     * the stored identity's provider, and both create flows set it explicitly
     * (the destination picker writes 'organization', the space suggester
     * 'space') — unlike a name or a color, which are user-editable and prove
     * nothing. The server-expanded owner also embeds a space object; it backs
     * the check up. An event whose destination cannot be established is not
     * treated as a space event: the push copies meetings, so only what is
     * provably a meeting leaves the platform.
     *
     * @param {Object} event the event to test
     * @returns {Boolean} true when the event belongs to a space calendar
     */
    isSpaceEvent(event) {
      const owner = event && event.calendar && event.calendar.owner;
      return !!owner && (owner.providerId === 'space' || !!owner.space);
    },
    /**
     * Removes the copy of a deleted event from the connected calendar.
     *
     * The removal is reported when it fails, because its failure is the one
     * the user cannot see: the meeting is gone from the agenda, so nothing on
     * screen suggests a copy of it is still standing on their phone, at a time
     * they are no longer expected anywhere.
     *
     * Only space events are mirrored (the push copies meetings, not the
     * calendars a user keeps privately in eXo), so only a space event's
     * deletion reaches the connected account.
     *
     * @param {Object} event the deleted event whose copy must go
     * @returns {Promise} resolves once the removal has been attempted
     */
    deleteEvent(event) {
      if (this.isSpaceEvent(event) && this.settings && this.settings.automaticPushEvents && this.connectedConnector && this.connectedConnector.canPush) {
        return this.$remoteEventConnector.removeEventFromConnector(this.connectedConnector, event, !!event.recurrence)
          .catch(error => this.announceCopyFailure(error, true))
          .finally(() => this.$root.$emit('agenda-refresh'));
      }
    },
    /**
     * Copies an event to the connected remote calendar when it is one the user
     * keeps: a meeting they accepted, or one they organise.
     *
     * Organised events used to be left out. The condition was attendance
     * alone, and an organiser is not necessarily in their own attendee list,
     * so an event created in eXo never reached the remote calendar — the case
     * that matters most, since it is what other clients need in order to show
     * the user as busy.
     *
     * An organiser who is in the list keeps their own answer, so declining an
     * event one organises still removes it remotely.
     *
     * @param {Object} event the event to copy
     * @returns {void}
     */
    pushEvent(event) {
      if (!event) {
        return;
      }
      const userAttendee = event.attendees
        && event.attendees.find(user => user.identity && user.identity.id === eXo.env.portal.userIdentityId);
      const organizer = String(event.creatorId) === String(eXo.env.portal.userIdentityId);
      if (event.acl && event.acl.attendee || organizer) {
        this.pushEventResponse(event, null, userAttendee && userAttendee.response || organizer && 'ACCEPTED' || null);
      }
    },
    /**
     * Writes the user's answer to the connected calendar: an accepted meeting
     * is copied there, any other answer takes the copy away.
     *
     * Both outcomes are reported when they fail. The copy is the whole point
     * of the feature and the user has no way of noticing it did not happen —
     * the agenda shows the meeting either way, and the calendar that missed it
     * is on another device. Silence here is what let a switch stay on for days
     * while nothing was reaching the account behind it.
     *
     * Only space events are pushed: the setting promises to copy the meetings
     * the user accepts or organises, and an event filed in one of their own
     * personal calendars is not that — copying it to their connected account
     * is at best redundant with what that account already holds. This is the
     * single gate for every trigger, since creation, update and every answer
     * flow funnel through here.
     *
     * @param {Object} event the event answered
     * @param {String} occurrenceId the occurrence answered, when only one was
     * @param {String} eventResponse the answer given
     * @returns {Promise} resolves once the copy has been written or removed
     */
    pushEventResponse(event, occurrenceId, eventResponse) {
      if (this.isSpaceEvent(event) && eventResponse && this.settings && this.settings.automaticPushEvents && this.connectedConnector && this.connectedConnector.canPush) {
        event.start = this.$agendaUtils.toRFC3339(event.start);
        event.end = this.$agendaUtils.toRFC3339(event.end);

        if (eventResponse.toLowerCase()  === 'accepted') {
          return this.$remoteEventConnector.pushEventToConnector(this.connectedConnector, event, !!event.recurrence)
            .catch(error => this.announceCopyFailure(error, false))
            .finally(() => this.$root.$emit('agenda-refresh'));
        } else {
          return this.$remoteEventConnector.removeEventFromConnector(this.connectedConnector, event, !!event.recurrence)
            .catch(error => this.announceCopyFailure(error, true))
            .finally(() => this.$root.$emit('agenda-refresh'));
        }
      }
    },
    /**
     * Says that the connected calendar did not take a change the agenda made
     * for the user, and keeps quiet for a while afterwards.
     *
     * The line drawn is one message per burst, not one per failed event: a
     * failing copy almost always fails for a reason that has nothing to do
     * with the particular meeting — the account is unreachable, or its
     * password was refused — so the second, third and tenth toast add nothing
     * the first did not say, while together they hide the agenda behind them.
     * A user who acts again once the quiet period has passed is told again,
     * so a problem that persists is not hidden either. The raw failure always
     * reaches the console, whether it was announced or not.
     *
     * @param {Object} error the failure the connector rejected with
     * @param {Boolean} removal whether the copy was being removed, not written
     * @returns {void}
     */
    announceCopyFailure(error, removal) {
      console.error('cannot update the copy of the event in the connected calendar', error);

      const now = Date.now();
      if (now - this.copyFailureAnnouncedAt < COPY_FAILURE_QUIET_PERIOD_MS) {
        return;
      }
      this.copyFailureAnnouncedAt = now;
      this.$root.$emit('alert-message', this.$t(this.copyFailureMessageKey(error, removal)), 'error');
    },
    /**
     * Chooses what the user is told about a failed copy.
     *
     * Refused credentials come first and drop the distinction between writing
     * and removing a copy: nothing will work until the account is connected
     * again, and that — not which operation happened to be running — is what
     * the user has to act on. The code is the one the connector rejects with,
     * read here rather than the message it carries, because tsdav turns a
     * rejected password into "cannot find principalUrl" and a user reading
     * that has no chance of guessing it means their password.
     *
     * @param {Object} error the failure the connector rejected with
     * @param {Boolean} removal whether the copy was being removed, not written
     * @returns {String} the translation key of the message to display
     */
    copyFailureMessageKey(error, removal) {
      if (error && error.code === 'caldav.error.credentials') {
        return 'agenda.pushEvents.copyCredentialsError';
      }
      return removal && 'agenda.pushEvents.copyRemovalError' || 'agenda.pushEvents.copyError';
    },
  },
};
</script>