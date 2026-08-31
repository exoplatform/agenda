<template>
  <div>
    <div class="d-flex">
      <div class="d-flex flex-column width-full">
        <div class="d-flex">
          <!-- the title says what the list is. It must not name a calendar:
               the list interleaves what several accounts hold, so naming one
               of them makes the heading lie about where the rows come from -->
          <div class=" my-auto text-no-wrap text-truncate font-weight-bold text-title-color">
            {{ $t('agenda.contemporaryEvents.title') }}
          </div>
          <v-spacer />
          <v-btn
            v-if="!hasConnectedAccount"
            :title="$t('agenda.connectYourPersonalAgenda')"
            :aria-label="$t('agenda.connectYourPersonalAgenda')"
            icon
            max-width="36"
            max-height="36"
            @click="openPersonalCalendarDrawer">
            <v-icon size="20" class="text-light-color">
              fas fa-plug
            </v-icon>
          </v-btn>
          <v-btn
            v-else
            :aria-label="$t('agenda.icsbutton')"
            :title="$t('agenda.icsbutton')"
            icon
            max-width="36"
            max-height="36"
            @click="downloadICS">
            <v-icon size="20" class="text-light-color">
              fa-calendar-plus
            </v-icon>
          </v-btn>
        </div>
        <!-- the accounts are named here, all of them, each carrying its own
             state: an account that could not be reached, or whose session
             expired, says so next to its own name instead of disappearing
             into a list the others answered -->
        <div
          v-if="hasConnectedAccount"
          class="d-flex flex-wrap contemporary-events-accounts">
          <a
            v-for="account in accountRoster"
            :key="account.key"
            :title="account.label"
            :class="account.stateClass"
            class="d-flex align-center me-4 icon-small-size contemporary-events-account"
            @click="openPersonalCalendarDrawer">
            <v-icon
              v-if="account.warning"
              size="16"
              class="me-1 warning--text">
              fa-exclamation-triangle
            </v-icon>
            <agenda-connector-avatar
              v-else
              :connector="account.connector"
              class="me-1"
              size="16" />
            <span class="text-truncate">{{ account.label }}</span>
          </a>
        </div>
        <div
          v-else
          class="text-subtitle d-flex">
          <div class="pe-6">
            {{ $t('agenda.synchronizeEventsWithPersonalCalendarSubTitle') }}
          </div>
        </div>
        <template v-if="loading || accountsLoading">
          <v-progress-linear indeterminate />
        </template>
        <template v-if="hasConnectedAccount">
          <template v-if="hasRemoteEvents">
            <agenda-connector-remote-event-item
              v-for="remoteEvent in displayedRemoteEvents"
              :key="remoteEventKey(remoteEvent)"
              :remote-event="remoteEvent"
              :connector="remoteEvent.connector"
              :hover-title="remoteEventTitle(remoteEvent)"
              :event="event"
              class="mt-5 remote-events-details"
              is-events-list />
          </template>
          <v-chip
            v-else-if="displayEmptyState"
            color="primary"
            class="border-radius my-2 contemporary-events-empty"
            outlined>
            <v-icon
              size="20"
              class="me-4"
              color="primary"
              depressed>
              fa-info-circle
            </v-icon>
            <span class="text--primary text-wrap">
              {{ emptyStateLabel }}
            </span>
          </v-chip>
        </template>
      </div>
    </div>
    <agenda-connectors-drawer :connectors="connectors" />
  </div>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => null
    },
    connectors: {
      type: Array,
      default: () => null
    },
    event: {
      type: Object,
      default: () => ({})
    },
  },
  data() {
    return {
      loading: false,
      remoteEvents: [],
      /*
       * The accounts whose last read failed. Kept apart from the events
       * because an account that could not answer is not an account that
       * answered "nothing": the panel has to be able to tell the two apart.
       */
      failedConnectors: [],
      /*
       * Collection names resolved for the events on display, keyed by
       * account and collection href.
       */
      resolvedCalendarNames: {},
      /*
       * Only the newest read may write what the panel shows: the screen can
       * move to another event while a read is in flight.
       */
      eventsRequestId: 0,
      fullDateFormat: {
        year: 'numeric',
        month: 'long',
        day: 'numeric',
      },
      timeFormat: {
        hour: '2-digit',
        minute: '2-digit',
      },
    };
  },
  computed: {
    /**
     * Every account the user has connected, whatever state it is in — the
     * roster names them all, including the ones that cannot answer.
     *
     * @returns {Array} the connected connectors
     */
    connectedConnectors() {
      return (this.connectors || []).filter(connector => connector.connected);
    },
    /**
     * Whether at least one account is connected: with none, the panel offers
     * to connect one instead of describing an empty list.
     *
     * @returns {Boolean} true when an account is connected
     */
    hasConnectedAccount() {
      return this.connectedConnectors.length > 0;
    },
    /**
     * The connected connectors whose browser session can be asked for remote
     * events: this panel shows what every connected account holds around the
     * event, not one account's view.
     *
     * @returns {Array} the signed-in connected connectors
     */
    signedInConnectors() {
      return this.connectedConnectors.filter(connector => connector.isSignedIn);
    },
    /**
     * Whether any connected account is still establishing its session, which
     * is not yet an answer about what it holds.
     *
     * @returns {Boolean} true while an account is loading
     */
    accountsLoading() {
      return this.connectedConnectors.some(connector => connector.loading);
    },
    /**
     * What has to be re-read: the set of accounts that can answer, not their
     * number. Connecting a second account leaves the count of "some account
     * is signed in" untouched, and the panel would keep showing what one
     * account held.
     *
     * @returns {String} a signature of the accounts that can be asked
     */
    connectorStatus() {
      return this.signedInConnectors.map(connector => connector.name).join('|');
    },
    /**
     * One entry per connected account, carrying that account's own state:
     * reachable accounts read as their address, an account that could not be
     * reached or whose session expired states the fact in its own words.
     *
     * @returns {Array} the roster entries to render
     */
    accountRoster() {
      return this.connectedConnectors.map(connector => {
        const account = connector.user || connector.name;
        const signedOut = !connector.isSignedIn && !connector.loading;
        const unreachable = !signedOut && this.failedConnectors.indexOf(connector) >= 0;
        let label = account;
        let stateClass = '';
        if (signedOut) {
          label = this.$t('agenda.contemporaryEvents.accountSignedOut', {0: account});
          stateClass = 'contemporary-events-account-signed-out';
        } else if (unreachable) {
          label = this.$t('agenda.contemporaryEvents.accountUnreachable', {0: account});
          stateClass = 'contemporary-events-account-unreachable';
        }
        return {
          key: connector.name,
          connector,
          label,
          stateClass,
          warning: signedOut || unreachable,
        };
      });
    },
    /**
     * Whether some connected account did not answer the last read — it
     * failed, or its session expired. An empty list then means "nothing on
     * the accounts that could be checked", never "nothing".
     *
     * @returns {Boolean} true when an account could not be asked or answered
     */
    hasUnansweredAccount() {
      return this.accountRoster.some(account => account.warning);
    },
    /**
     * Whether the "nothing found" message may be shown at all: never while a
     * read is still running, since an unfinished read is not an answer.
     *
     * @returns {Boolean} true when the empty state is an honest statement
     */
    displayEmptyState() {
      return !this.loading && !this.accountsLoading;
    },
    /**
     * What an empty list says. It must not claim there is nothing when an
     * account could not be asked: the roster already names which one, and
     * this sentence stops short of an answer it does not have.
     *
     * @returns {String} the empty-state sentence
     */
    emptyStateLabel() {
      return this.hasUnansweredAccount
        ? this.$t('agenda.contemporaryEvents.noEventsPartial')
        : this.$t('agenda.contemporaryEvents.noEvents');
    },
    hasRemoteEvents() {
      return this.displayedRemoteEvents && this.displayedRemoteEvents.length;
    },
    displayedRemoteEvents() {
      const remoteEventsToDisplay = this.remoteEvents && this.remoteEvents.slice() || [];
      // Avoid to have same event from remote and local store (pushed events from local store)
      if (remoteEventsToDisplay.length) {
        // Only a copy of THIS event is dropped. The identifiers are compared
        // once known to exist: an event that was never copied to a calendar
        // account carries none, and comparing two absent ones matched the
        // first row of the list, which then silently disappeared.
        const eventRemoteId = this.event && this.event.remoteId;
        const parentRemoteId = this.event && this.event.parent && this.event.parent.remoteId;
        const index = remoteEventsToDisplay.findIndex(remoteEvent =>
          !!eventRemoteId && (remoteEvent.id === eventRemoteId || remoteEvent.recurringEventId === eventRemoteId)
          || !!parentRemoteId && remoteEvent.recurringEventId === parentRemoteId);
        if (index >= 0) {
          remoteEventsToDisplay.splice(index, 1);
        }
        remoteEventsToDisplay.push(this.event);
        remoteEventsToDisplay.sort((event1, event2) => {
          const eventStart1 = this.$agendaUtils.toDate(event1.start || event1.startDate).getTime();
          const eventStart2 = this.$agendaUtils.toDate(event2.start || event2.startDate).getTime();
          return eventStart1 - eventStart2;
        });
      }
      return remoteEventsToDisplay;
    },
  },
  watch: {
    connectorStatus() {
      this.refreshRemoteEvents();
    },
    /**
     * The panel is reused from one event to the next, so a new event is read
     * anew: what it kept describes the day of the event that was on screen
     * before, which is not this one's.
     *
     * @returns {void}
     */
    event() {
      this.remoteEvents = [];
      this.failedConnectors = [];
      this.refreshRemoteEvents();
    },
  },
  created() {
    this.$root.$emit('agenda-connectors-init');
    this.refreshRemoteEvents();
  },
  methods: {
    openPersonalCalendarDrawer() {
      this.$root.$emit('agenda-connectors-drawer-open');
    },
    refreshRemoteEvents() {
      this.retrieveRemoteEvents();
    },
    /**
     * Fetches what every signed-in connected account holds around the event's
     * days and merges it into one deduplicated list, each entry tagged with
     * the account it came from.
     *
     * <p>
     * An account that fails is recorded as failed rather than folded in as an
     * account holding nothing: those two are different answers and the panel
     * says which one it got.
     *
     * @returns {void}
     */
    retrieveRemoteEvents() {
      const connectors = this.signedInConnectors;
      // The window is derived from the event, never written back into it: the
      // prop's Date objects belong to the screen showing the event, and
      // widening them to whole days here moved the event itself.
      const eventStartDay = this.$agendaUtils.toDate(this.event && this.event.startDate);
      const eventEndDay = this.$agendaUtils.toDate(this.event && this.event.endDate);
      if (!connectors.length || !eventStartDay || !eventEndDay) {
        this.eventsRequestId++;
        this.remoteEvents = [];
        this.failedConnectors = [];
        this.loading = false;
        return;
      }

      // Start of the day of start date
      eventStartDay.setHours(0);
      eventStartDay.setMinutes(0);

      // End of the day of end date
      eventEndDay.setHours(23);
      eventEndDay.setMinutes(59);

      const startDateRFC3359 = this.$agendaUtils.toRFC3339(eventStartDay, false, true);
      const endDateRFC3359 = this.$agendaUtils.toRFC3339(eventEndDay, false, true);

      this.loading = true;
      // The same guard the left panel uses: the dialog is reused from one
      // event to the next, and a read that lands after the screen moved on
      // would describe the previous event's day.
      const requestId = ++this.eventsRequestId;
      // Every signed-in account is asked, and each fails on its own: one
      // unreachable account must not blank the events the others returned
      Promise.all(connectors.map(connector =>
        connector.getEvents(startDateRFC3359, endDateRFC3359)
          .then(events => {
            if (events) {
              events.forEach(event => {
                event.startDate = event.start && this.$agendaUtils.toDate(event.start) || null;
                event.endDate = event.end && this.$agendaUtils.toDate(event.end) || null;
              });
            }
            return {connector, events: events || []};
          })
          .catch(error => {
            console.error('Error retrieving remote events', connector.name, error);
            // No events array at all: an account that could not answer must
            // not reach the merge as an account that answered "nothing".
            return {connector, failed: true};
          })))
        .then(resultsByConnector => {
          if (requestId !== this.eventsRequestId) {
            // A newer read was started since: its answer is the one that
            // describes the event now on screen.
            return;
          }
          this.failedConnectors = resultsByConnector
            .filter(result => result.failed)
            .map(result => result.connector);
          this.remoteEvents = this.$agendaUtils.mergeRemoteEvents(resultsByConnector.filter(result => !result.failed));
          this.loading = false;
          this.resolveRemoteCalendarNames(requestId);
        });
    },
    /**
     * Asks each account what it calls the collections the displayed events
     * were read from, so a row can say where the event lives rather than how
     * it arrived. Resolved once per account and collection; the answer is
     * dropped when a newer read has started meanwhile.
     *
     * @param {Number} requestId the read this resolution belongs to
     * @returns {void}
     */
    resolveRemoteCalendarNames(requestId) {
      this.remoteEvents.forEach(remoteEvent => {
        const key = this.remoteCalendarKey(remoteEvent);
        if (!key || key in this.resolvedCalendarNames) {
          return;
        }
        this.$set(this.resolvedCalendarNames, key, null);
        this.$remoteEventConnector.remoteCalendarName(remoteEvent.connector, remoteEvent.calendarId)
          .then(name => {
            if (requestId === this.eventsRequestId) {
              this.$set(this.resolvedCalendarNames, key, name);
            } else {
              // A newer read is on screen: this answer is not about it, and
              // the placeholder must not keep the collection unnameable.
              this.$delete(this.resolvedCalendarNames, key);
            }
          });
      });
    },
    /**
     * Identifies a collection across the accounts on display: the same href
     * read from two accounts is not the same collection.
     *
     * @param {Object} remoteEvent the event as its account returned it
     * @returns {String} the key, empty when the event carries no collection
     */
    remoteCalendarKey(remoteEvent) {
      if (!remoteEvent || !remoteEvent.calendarId || !remoteEvent.connector) {
        return '';
      }
      return `${remoteEvent.connector.name}|${remoteEvent.calendarId}`;
    },
    /**
     * The hover text of a row: the event's title, plus where it lives when
     * the row came from a connected account.
     *
     * <p>
     * The collection's own name when the account can give it, else the
     * account it belongs to, else the href — never the connector's name,
     * which answers how the event arrived and not where it lives.
     *
     * @param {Object} remoteEvent the event the row renders
     * @returns {String} the hover text
     */
    remoteEventTitle(remoteEvent) {
      const key = this.remoteCalendarKey(remoteEvent);
      if (!key) {
        return remoteEvent && remoteEvent.summary || '';
      }
      const resolvedName = this.resolvedCalendarNames[key];
      const account = remoteEvent.connector.user;
      let calendarLabel;
      if (resolvedName) {
        calendarLabel = resolvedName;
      } else if (account) {
        calendarLabel = this.$t('agenda.remoteEvent.calendarOfAccount', {0: account});
      } else {
        calendarLabel = remoteEvent.calendarId;
      }
      return this.$t('agenda.contemporaryEvents.rowCalendar', {0: remoteEvent.summary || '', 1: calendarLabel});
    },
    /**
     * Identifies a row: the same occurrence read from two accounts is two
     * rows, and an object is not a key — Vue cannot tell two of them apart,
     * so it re-created every row on each read.
     *
     * @param {Object} remoteEvent the event the row renders
     * @returns {String} a stable key for the row
     */
    remoteEventKey(remoteEvent) {
      const connectorName = remoteEvent.connector && remoteEvent.connector.name || '';
      const start = this.$agendaUtils.toDate(remoteEvent.start || remoteEvent.startDate);
      return `${connectorName}|${remoteEvent.id || remoteEvent.summary || ''}|${start && start.getTime() || ''}`;
    },
    downloadICS() {
      this.$emit('download-ics', this.event);
    }
  }
};
</script>
