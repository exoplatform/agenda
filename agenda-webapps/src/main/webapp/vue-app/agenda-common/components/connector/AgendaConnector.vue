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
    /**
     * The accounts the settings say are connected, one per provider at most.
     * Read from the list the settings now carry, falling back to the two
     * legacy fields for a client still holding a settings object fetched
     * before the list existed.
     *
     * @returns {Array} the accounts, each {providerName, remoteUserId, pushEnabled}
     */
    connectedAccounts() {
      if (!this.settings) {
        return [];
      }
      if (this.settings.connectedConnectors && this.settings.connectedConnectors.length) {
        return this.settings.connectedConnectors;
      }
      if (this.settings.connectedRemoteProvider) {
        return [{
          providerName: this.settings.connectedRemoteProvider,
          remoteUserId: this.settings.connectedRemoteUserId,
          pushEnabled: true,
        }];
      }
      return [];
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
    /**
     * Rebuilds the connectors list from the extension registry, marking each
     * one enabled from the administrator's providers and connected from the
     * user's own account on that provider. Several connectors can be
     * connected at once — the settings carry one account per provider — so
     * each connector reads its own account rather than a single shared one.
     *
     * @returns {void}
     */
    refreshConnectorsList() {
      // Get list of connectors from extensionRegistry
      const connectors = extensionRegistry.loadExtensions('agenda', 'connectors') || [];

      // Check connectors 'enablement' status from store
      if (this.remoteProviders && this.remoteProviders.length) {
        connectors
          .forEach(connector => {
            const connectorObj = this.remoteProviders.find(connectorSettings => connectorSettings.name === connector.name);
            const account = this.connectedAccounts.find(connectedAccount => connectedAccount.providerName === connector.name);
            connector.enabled = connectorObj && connectorObj.enabled || false;
            connector.apiKey = connectorObj && connectorObj.apiKey || '';
            connector.connected = connector.enabled && !!account;
            connector.user = connector.connected && account.remoteUserId || '';
            // Whether this account receives copies of the user's meetings:
            // per-account, so opting one account out never silences the others
            connector.pushEnabled = !account || account.pushEnabled !== false;
          });
      } else {
        connectors.forEach(connector => connector.enabled = false);
      }

      this.initConnectors();

      this.$emit('connectors-loaded', connectors);
    },
    /**
     * Offers the step that creates the calendar receiving the copies, unless
     * the account already has one.
     *
     * Reconnecting an account that was set up before — the common case after
     * a password change, or after moving between servers — leaves its
     * destination calendar sitting on the server. Asking again to create
     * what is already there reads as the connection having lost something,
     * and invites the user to make a second calendar beside the first.
     *
     * A check that fails answers nothing, so the step is offered as it was
     * before: an account left with no destination copies nowhere, which is
     * the worse of the two ways to be wrong.
     *
     * @param {Object} connector the connector just connected
     * @returns {void}
     */
    offerMirrorCalendarUnlessPresent(connector) {
      Promise.resolve(this.readMirrorCalendar(connector))
        .then(mirror => {
          if (!mirror) {
            this.$root.$emit('agenda-connector-mirror-calendar-open', connector);
          }
        })
        .catch(() => this.$root.$emit('agenda-connector-mirror-calendar-open', connector));
    },
    /**
     * The calendar an account already uses for the copies, asked of the
     * connector in whichever way it answers: some hold the calendar itself,
     * the others only its id among the calendars they can list.
     *
     * @param {Object} connector the connector to ask
     * @returns {Promise} resolves with the calendar, or a falsy value when
     *          the account has none
     */
    readMirrorCalendar(connector) {
      if (typeof connector.getMirrorCalendar === 'function') {
        return connector.getMirrorCalendar();
      }
      if (typeof connector.getMirrorCalendarId !== 'function') {
        return Promise.resolve(null);
      }
      return Promise.resolve(connector.getMirrorCalendarId())
        .then(mirrorId => mirrorId
          && typeof connector.listCalendars === 'function'
          && Promise.resolve(connector.listCalendars())
            .then(calendars => (calendars || []).find(calendar => String(calendar.id) === String(mirrorId)))
          || null);
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
     * Connects a connector to its remote account: delegates the connection to
     * the connector itself, then stores the connected user beside the
     * accounts already held — connecting Google no longer evicts the CalDAV
     * account backing My Calendars, or vice versa; accounts are additive, one
     * per provider. Once the connection succeeded, when the connector can
     * create a calendar on the remote server, the mirror calendar step is
     * offered — the destination the pushed meetings will be written to — so a
     * refused creation is dealt with at connect time, not at first push.
     *
     * @param {Object} connector the connector to connect
     * @returns {Promise} resolves once the connection is established and stored
     */
    connect(connector) {
      this.errorMessage = null;

      this.$set(connector, 'loading', true);
      return Promise.resolve()
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
        // The account is connected, but its calendars are not here yet: what
        // makes them appear is the first synchronisation, and it has not run.
        // Refreshing the panels now shows every collection under Remote —
        // the heading for calendars eXo is *not* showing — because that is
        // precisely what they still are. They move to Personal only once
        // something materialises them, and until this waited for that, the
        // only thing that corrected the display was the user reloading.
        .then(() => typeof connector.sync === 'function'
          && Promise.resolve(connector.sync()).catch(error => {
            // A first synchronisation that fails is not a failed connection:
            // the account is connected either way, and the sweep will try
            // again. The panels are refreshed regardless, so a collection
            // that did materialise is not left looking remote.
            console.error('the first synchronisation after connecting did not complete', error);
          }))
        .then(() => {
          this.$set(connector, 'loading', false);
          this.$root.$emit('agenda-settings-refresh');
          this.$root.$emit('agenda-refresh-personal-calendars');
          this.refreshConnectorsList();
          // Not offered where connecting is one preference among others: a
          // drawer taking over the page is for the agenda, where connecting
          // is the task at hand. In the settings the same step is reached by
          // turning the copy switch on, which is what asks for it.
          if (this.offerMirrorCalendar && connector.canCreateCalendar) {
            this.offerMirrorCalendarUnlessPresent(connector);
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
    /**
     * Disconnects one connector's account: signs the browser session out when
     * one is held, then removes the stored account.
     *
     * @param {Object} connector the connector to disconnect
     * @returns {Promise} resolves once the account is removed
     */
    disconnect(connector) {
      //disconnect from connected browser
      if (connector.isSignedIn) {
        return connector.disconnect().then(() => this.resetConnector(connector));
      } else {//disconnect from other browser
        return this.resetConnector(connector);
      }
    },
    /**
     * Removes this connector's stored account and clears its runtime flags.
     * The removal names the connector: with several accounts held at once,
     * disconnecting one must leave the others standing.
     *
     * @param {Object} connector the connector whose account is removed
     * @returns {Promise} resolves once the settings no longer hold the account
     */
    resetConnector(connector) {
      this.$set(connector, 'loading', true);
      return this.$settingsService.resetUserConnector(connector.name)
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
          // The same refresh connecting asks for, and for the mirror-image
          // reason. Disconnecting removes the calendars materialised from the
          // account, so the panels are describing calendars that no longer
          // exist — the personal list still offering them and the remote list
          // still hiding them as bound. Only a reload corrected it.
          this.$root.$emit('agenda-refresh-personal-calendars');
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
    /**
     * Reconciles one connector's browser session with the account the
     * settings hold for that same connector — its own account, not a single
     * shared one: with several accounts connected at once, Google's session
     * must be compared to the Google account, never to the CalDAV one.
     *
     * @param {Object} connector the connector whose session is reconciled
     * @param {Object} connectedUser the user the browser session holds
     * @returns {void}
     */
    cleanConnectorStatus(connector, connectedUser) {
      this.$set(connector, 'error', '');

      if (connector && connector.user) {
        //if user is connected with different account from other browser
        if (connectedUser && connectedUser.user !== connector.user) {
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
     * Whether this event is one the connected account should receive.
     *
     * Two destinations, and deliberately two different conditions.
     *
     * A space meeting is a <em>copy</em> made into a calendar eXo creates on
     * the account, and the user opts into that with the copy setting. Without
     * the setting there is no copy and no calendar to hold one.
     *
     * An event of a calendar the connector materialised is not a copy. That
     * calendar exists because the user connected the account and the
     * collection behind it became a calendar here; keeping the two sides equal
     * is what the calendar is for, and a second switch to permit it would ask
     * the user to opt into the thing they already opted into. So this does not
     * consult the copy setting — a bound calendar synchronises both ways
     * because it is bound.
     *
     * Connectors that fetch remotely without materialising anything declare
     * nothing, so they keep exactly the behaviour they had — for them a
     * personal calendar has no counterpart to write into.
     *
     * Which calendars have a collection is the connector's business, not this
     * component's: it says whether it does that at all, and the server it
     * pushes to decides per calendar and answers that nothing was copied when
     * there is nowhere to copy to.
     *
     * With several accounts connected at once, the question is asked per
     * account: a space meeting is copied to every connected account able to
     * receive it, minus the ones whose per-account switch opts them out; a
     * bound calendar's event reaches only the account that materialised the
     * calendar, whatever the copy switches say.
     *
     * @param {Object} connector the connected connector asked to receive it
     * @param {Object} event the event about to be copied or removed
     * @returns {Boolean} true when that connector's account should receive it
     */
    shouldReachAccount(connector, event) {
      if (this.isSpaceEvent(event)) {
        return !!(this.settings && this.settings.automaticPushEvents) && connector.pushEnabled !== false;
      }
      return !!connector.pushesOwnCalendars && this.isOwnCalendarEvent(event);
    },
    /**
     * The connected accounts an event's copy or removal must be written to:
     * every connected connector able to push whose account should receive
     * this event. Copies go to every account that can take them — one
     * behaviour, per-account opt-out switches — rather than to one chosen
     * target.
     *
     * @param {Object} event the event about to be copied or removed
     * @returns {Array} the connectors to write to, possibly empty
     */
    pushTargets(event) {
      return (this.connectors || [])
        .filter(connector => connector
          && connector.connected
          && connector.canPush
          && this.shouldReachAccount(connector, event));
    },
    /**
     * Whether the event sits in a calendar this user owns.
     *
     * The owner is matched on the remote id rather than the identity id: it
     * is the field every caller of this component already populates, and the
     * one the profile links are built from.
     *
     * @param {Object} event the event to place
     * @returns {Boolean} true when the calendar belongs to the current user
     */
    isOwnCalendarEvent(event) {
      const owner = event && event.calendar && event.calendar.owner;
      return !!owner && owner.providerId === 'organization' && owner.remoteId === eXo.env.portal.userName;
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
      const targets = this.pushTargets(event);
      if (targets.length) {
        return Promise.all(targets.map(connector =>
          this.$remoteEventConnector.removeEventFromConnector(connector, event, !!event.recurrence)
            .catch(error => this.announceCopyFailure(connector, error, true))))
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
      const targets = eventResponse && this.pushTargets(event) || [];
      if (targets.length) {
        event.start = this.$agendaUtils.toRFC3339(event.start);
        event.end = this.$agendaUtils.toRFC3339(event.end);

        const removal = eventResponse.toLowerCase() !== 'accepted';
        return Promise.all(targets.map(connector => {
          const operation = removal
            ? this.$remoteEventConnector.removeEventFromConnector(connector, event, !!event.recurrence)
            : this.$remoteEventConnector.pushEventToConnector(connector, event, !!event.recurrence);
          // Each account fails or succeeds on its own: one unreachable
          // account must not stop the copy from reaching the others
          return operation.catch(error => this.announceCopyFailure(connector, error, removal));
        })).finally(() => this.$root.$emit('agenda-refresh'));
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
     * @param {Object} connector the connector whose account refused the copy
     * @param {Object} error the failure the connector rejected with
     * @param {Boolean} removal whether the copy was being removed, not written
     * @returns {void}
     */
    announceCopyFailure(connector, error, removal) {
      console.error('cannot update the copy of the event in the connected calendar', connector && connector.name, error);

      const now = Date.now();
      if (now - this.copyFailureAnnouncedAt < COPY_FAILURE_QUIET_PERIOD_MS) {
        return;
      }
      this.copyFailureAnnouncedAt = now;
      this.$root.$emit('alert-message', this.$t(this.copyFailureMessageKey(connector, error, removal)), 'error');
    },
    /**
     * Chooses what the user is told about a failed copy.
     *
     * Refused credentials come first and drop the distinction between writing
     * and removing a copy: nothing will work until the account is connected
     * again, and that — not which operation happened to be running — is what
     * the user has to act on. The code is read rather than the message,
     * because a connector may turn a rejected password into something like
     * "cannot find principalUrl", which no user could read as their password.
     *
     * Which code carries that meaning is the connector's to declare, through
     * `credentialsErrorCode`; a connector declaring none simply falls to the
     * generic message. This component serves every connector, so it holds no
     * single add-on's vocabulary.
     *
     * @param {Object} connector the connector whose account refused the copy
     * @param {Object} error the failure the connector rejected with
     * @param {Boolean} removal whether the copy was being removed, not written
     * @returns {String} the translation key of the message to display
     */
    copyFailureMessageKey(connector, error, removal) {
      const credentialsCode = connector && connector.credentialsErrorCode;
      if (credentialsCode && error && error.code === credentialsCode) {
        return 'agenda.pushEvents.copyCredentialsError';
      }
      return removal && 'agenda.pushEvents.copyRemovalError' || 'agenda.pushEvents.copyError';
    },
  },
};
</script>