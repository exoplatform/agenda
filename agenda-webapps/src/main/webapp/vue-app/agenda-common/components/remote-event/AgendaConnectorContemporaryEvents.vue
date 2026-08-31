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
        <!-- the sources are named here, all of them, each carrying its own
             state: an account that could not be reached, or whose session
             expired, says so next to its own name instead of disappearing
             into a list the others answered. eXo's own calendars are not an
             account and hold no standing entry — they appear here only when
             they could not be read, which is the one thing about them the
             roster has to say -->
        <div
          v-if="sourceRoster.length"
          class="d-flex flex-wrap contemporary-events-accounts">
          <component
            :is="account.connector ? 'a' : 'div'"
            v-for="account in sourceRoster"
            :key="account.key"
            :title="account.label"
            :class="account.stateClass"
            class="d-flex align-center me-4 icon-small-size contemporary-events-account"
            @click="account.connector && openPersonalCalendarDrawer()">
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
          </component>
        </div>
        <div
          v-if="!hasConnectedAccount"
          class="text-subtitle d-flex">
          <div class="pe-6">
            {{ $t('agenda.synchronizeEventsWithPersonalCalendarSubTitle') }}
          </div>
        </div>
        <template v-if="loading || accountsLoading">
          <v-progress-linear indeterminate />
        </template>
        <!-- the list is no longer gated on an account being connected: eXo's
             own calendars answer whether or not one is, and hiding what they
             hold behind a connector is the very emptiness this panel had -->
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
      </div>
    </div>
    <agenda-connectors-drawer :connectors="connectors" />
  </div>
</template>

<script>
/*
 * The attendee responses that put an event on the viewer's schedule. Every
 * response but DECLINED: a meeting the viewer turned down is not on their
 * schedule, while one they have not answered yet is exactly what they need
 * to see before answering this one.
 */
const SCHEDULED_RESPONSES = ['ACCEPTED', 'NEEDS_ACTION', 'TENTATIVE'];

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
      remoteLoading: false,
      exoLoading: false,
      remoteEvents: [],
      /*
       * What eXo's own calendars hold around the event — including the
       * calendars materialised from a connected account, whose events the
       * live read stops reporting the moment they are imported.
       */
      exoEvents: [],
      /*
       * Whether the last read of eXo's own calendars failed. Kept for the
       * same reason as failedConnectors: a source that could not answer is
       * not a source that answered "nothing".
       */
      exoReadFailed: false,
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
     * Whether either source is still being read: an unfinished read is not
     * an answer, from eXo's own calendars as much as from an account.
     *
     * @returns {Boolean} true while a read is in flight
     */
    loading() {
      return this.remoteLoading || this.exoLoading;
    },
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
     * Every source the roster names: one entry per connected account, always,
     * plus eXo's own calendars when — and only when — they could not be read.
     *
     * <p>
     * eXo's own calendars hold no standing entry because they are not an
     * account: there is nothing to connect, nothing to reconnect, and no
     * address to name. What the roster owes them is the one state the user
     * cannot infer from the list, which is that they failed.
     *
     * @returns {Array} the roster entries to render
     */
    sourceRoster() {
      const roster = this.accountRoster.slice();
      if (this.exoReadFailed) {
        roster.push({
          key: 'exo-calendars',
          connector: null,
          label: this.$t('agenda.contemporaryEvents.exoCalendarsUnreachable'),
          stateClass: 'contemporary-events-account-unreachable',
          warning: true,
        });
      }
      return roster;
    },
    /**
     * Whether some source did not answer the last read — an account that
     * failed or whose session expired, or eXo's own calendars. An empty list
     * then means "nothing on the sources that could be checked", never
     * "nothing".
     *
     * @returns {Boolean} true when a source could not be asked or answered
     */
    hasUnansweredSource() {
      return this.sourceRoster.some(account => account.warning);
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
      return this.hasUnansweredSource
        ? this.$t('agenda.contemporaryEvents.noEventsPartial')
        : this.$t('agenda.contemporaryEvents.noEvents');
    },
    hasRemoteEvents() {
      return this.displayedRemoteEvents && this.displayedRemoteEvents.length;
    },
    /**
     * The two sources folded into one list, without double-counting the
     * meetings both of them hold.
     *
     * <p>
     * The identity that spans the two is the remote object's own identifier
     * together with the occurrence's start: an event imported into an eXo
     * calendar records the UID of the object it was imported from, and an
     * eXo event pushed to an account records the identifier the account
     * issued for it — in both cases the very identifier the live read returns
     * as the event's <code>id</code>. Start is part of the key because the
     * occurrences of a recurring series all share that identifier.
     *
     * <p>
     * The key is deliberately NOT scoped by provider name. A materialised
     * event records the constant <code>agenda.caldavCalendar</code>, while a
     * multi-server CalDAV account is bound under its own server's provider
     * name, so scoping would stop matching exactly where materialisation is
     * used. Nothing is lost by leaving it out: a UID is unique by
     * specification and a provider-issued identifier is an opaque token, so
     * two unrelated events sharing one AND starting at the same instant is
     * not a case that exists.
     *
     * <p>
     * When both sources hold the meeting, the eXo copy is the one kept — it
     * is the copy the viewer can act on, and keeping it makes a meeting that
     * happens to be readable twice render exactly like the ordinary
     * materialised one instead of depending on which read landed.
     *
     * @returns {Array} the events of both sources, each of them once
     */
    scheduledEvents() {
      const exoEvents = (this.exoEvents || []).filter(exoEvent => !this.isCurrentEvent(exoEvent));
      const knownRemoteOccurrences = new Set();
      exoEvents.forEach(exoEvent => {
        const key = this.exoRemoteOccurrenceKey(exoEvent);
        if (key) {
          knownRemoteOccurrences.add(key);
        }
      });
      const liveEvents = (this.remoteEvents || []).filter(remoteEvent => {
        const key = this.liveOccurrenceKey(remoteEvent);
        return !key || !knownRemoteOccurrences.has(key);
      });
      return exoEvents.concat(liveEvents);
    },
    displayedRemoteEvents() {
      const remoteEventsToDisplay = this.scheduledEvents.slice();
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
      this.exoEvents = [];
      this.failedConnectors = [];
      this.exoReadFailed = false;
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
    /**
     * Re-reads both sources for the event on screen under one request id, so
     * that a read landing after the dialog moved on is dropped whichever
     * source it came from.
     *
     * @returns {void}
     */
    refreshRemoteEvents() {
      const period = this.readPeriod();
      // The same guard the left panel uses: the dialog is reused from one
      // event to the next, and a read that lands after the screen moved on
      // would describe the previous event's day.
      const requestId = ++this.eventsRequestId;
      this.retrieveExoEvents(requestId, period);
      this.retrieveRemoteEvents(requestId, period);
    },
    /**
     * The whole days the event spans, as the two sources are both asked for
     * them.
     *
     * <p>
     * Derived from the event, never written back into it: the prop's Date
     * objects belong to the screen showing the event, and widening them to
     * whole days here moved the event itself.
     *
     * @returns {Object} `{start, end}` in RFC3339, null when the event
     *          carries no dates to widen
     */
    readPeriod() {
      const eventStartDay = this.$agendaUtils.toDate(this.event && this.event.startDate);
      const eventEndDay = this.$agendaUtils.toDate(this.event && this.event.endDate);
      if (!eventStartDay || !eventEndDay) {
        return null;
      }

      // Start of the day of start date
      eventStartDay.setHours(0);
      eventStartDay.setMinutes(0);

      // End of the day of end date
      eventEndDay.setHours(23);
      eventEndDay.setMinutes(59);

      return {
        start: this.$agendaUtils.toRFC3339(eventStartDay, false, true),
        end: this.$agendaUtils.toRFC3339(eventEndDay, false, true),
      };
    },
    /**
     * Fetches what eXo's own calendars hold around the event's days.
     *
     * <p>
     * This is the source that answers for a materialised account: once a
     * remote collection is imported its events ARE eXo events, and the
     * connector stops reporting them precisely because they were imported —
     * so a panel asking only the connector reads a stream that is empty by
     * construction.
     *
     * <p>
     * The existing events endpoint answers it as it stands: it already
     * filters on the viewer as an attendee and on the response types, and it
     * already fills `remoteId` on every event that carries one, which is what
     * the deduplication against the live read needs. Nothing new is added
     * server-side.
     *
     * <p>
     * A read that fails is recorded as failed, for the same reason an account
     * that fails is: an empty list must not be able to mean two things.
     *
     * @param {Number} requestId the read this answer belongs to
     * @param {Object} period the days to read, null when the event has none
     * @returns {void}
     */
    retrieveExoEvents(requestId, period) {
      if (!period) {
        this.exoEvents = [];
        this.exoReadFailed = false;
        this.exoLoading = false;
        return;
      }
      this.exoLoading = true;
      const attendeeIdentityId = eXo && eXo.env && eXo.env.portal && eXo.env.portal.userIdentityId || null;
      return this.$eventService.getEvents(
        null,
        [],
        attendeeIdentityId,
        period.start,
        period.end,
        0,
        SCHEDULED_RESPONSES,
        '')
        .then(data => {
          if (requestId !== this.eventsRequestId) {
            return;
          }
          const events = data && data.events || [];
          events.forEach(event => {
            event.startDate = event.start && this.$agendaUtils.toDate(event.start) || null;
            event.endDate = event.end && this.$agendaUtils.toDate(event.end) || null;
          });
          this.exoEvents = events;
          this.exoReadFailed = false;
          this.exoLoading = false;
        })
        .catch(error => {
          if (requestId !== this.eventsRequestId) {
            return;
          }
          console.error('Error retrieving events of the user calendars', error);
          this.exoEvents = [];
          this.exoReadFailed = true;
          this.exoLoading = false;
        });
    },
    /**
     * Fetches what every signed-in connected account holds around the event's
     * days and merges it into one deduplicated list, each entry tagged with
     * the account it came from.
     *
     * <p>
     * For a CalDAV account this is now the collections eXo never imported:
     * the connector already leaves out the ones it materialised, and what
     * they hold comes from eXo's own calendars instead. For a connector that
     * materialises nothing — Google, Office 365, Exchange — it stays the
     * whole account.
     *
     * <p>
     * An account that fails is recorded as failed rather than folded in as an
     * account holding nothing: those two are different answers and the panel
     * says which one it got.
     *
     * @param {Number} requestId the read this answer belongs to
     * @param {Object} period the days to read, null when the event has none
     * @returns {void}
     */
    retrieveRemoteEvents(requestId, period) {
      const connectors = this.signedInConnectors;
      if (!connectors.length || !period) {
        this.remoteEvents = [];
        this.failedConnectors = [];
        this.remoteLoading = false;
        return;
      }

      this.remoteLoading = true;
      // Every signed-in account is asked, and each fails on its own: one
      // unreachable account must not blank the events the others returned
      Promise.all(connectors.map(connector =>
        connector.getEvents(period.start, period.end)
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
          // the same partition three other views need, so it lives in
          // AgendaUtils rather than once per view
          const sources = this.$agendaUtils.splitRemoteEventResults(resultsByConnector);
          this.remoteEvents = sources.events;
          this.failedConnectors = sources.failedConnectors;
          this.remoteLoading = false;
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
     * Whether an event read from eXo's own calendars is the event the panel
     * is describing — which the list carries once, of its own accord, and
     * must not carry twice.
     *
     * <p>
     * A computed occurrence of a recurring series carries no id of its own —
     * the endpoint sends it as zero, with the series as its parent — so the
     * series answers for it. The start is compared alongside, because the
     * occurrences of that series all share it: another occurrence of this
     * very series, on this very day, is a different row and has to survive.
     *
     * @param {Object} exoEvent an event of the viewer's own calendars
     * @returns {Boolean} true when the row would repeat the current event
     */
    isCurrentEvent(exoEvent) {
      const identity = this.eventIdentity(this.event);
      return !!identity && this.eventIdentity(exoEvent) === identity;
    },
    /**
     * What identifies one occurrence among eXo's own events: the event's id,
     * or the series' id when the event is a computed occurrence, together
     * with the start that tells the siblings apart.
     *
     * @param {Object} event an event of the viewer's own calendars
     * @returns {String} the identity, empty when the event carries no id
     */
    eventIdentity(event) {
      const eventId = event && (event.id || event.parent && event.parent.id);
      if (!eventId) {
        return '';
      }
      return `${eventId}|${this.startInstant(event)}`;
    },
    /**
     * The instant a row starts at, as a comparable value.
     *
     * <p>
     * An all-day event is keyed on its calendar day rather than on an
     * instant: the two sources spell the start of a whole day differently
     * (one with the day's midnight in the viewer's zone, the other with a
     * date alone), and comparing those as instants would make the same
     * all-day meeting look like two.
     *
     * @param {Object} event an event from either source
     * @returns {String} the key, empty when the event carries no start
     */
    startInstant(event) {
      const start = event && this.$agendaUtils.toDate(event.start || event.startDate);
      if (!start) {
        return '';
      }
      if (event.allDay) {
        return `${start.getFullYear()}-${start.getMonth() + 1}-${start.getDate()}`;
      }
      return String(start.getTime());
    },
    /**
     * The identity an event of eXo's own calendars shares with the live read,
     * when it has one: the identifier of the remote object it was imported
     * from, or the one the account issued when it was pushed there.
     *
     * <p>
     * A computed occurrence carries no mapping of its own — the endpoint
     * records it on the series — so the parent's identifier answers for it,
     * separated from its siblings by the start.
     *
     * @param {Object} exoEvent an event of the viewer's own calendars
     * @returns {String} the key, empty when the event has no remote identity
     */
    exoRemoteOccurrenceKey(exoEvent) {
      const remoteId = exoEvent && (exoEvent.remoteId || exoEvent.parent && exoEvent.parent.remoteId);
      if (!remoteId) {
        return '';
      }
      return `${remoteId}|${this.startInstant(exoEvent)}`;
    },
    /**
     * The same identity read off a live event, whose `id` is the identifier
     * the account knows it by.
     *
     * @param {Object} remoteEvent an event as its account returned it
     * @returns {String} the key, empty when the event carries no identifier
     */
    liveOccurrenceKey(remoteEvent) {
      if (!remoteEvent || !remoteEvent.id) {
        return '';
      }
      return `${remoteEvent.id}|${this.startInstant(remoteEvent)}`;
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
        // A row from eXo's own calendars keeps the same promise by the means
        // available to it: the events endpoint already names the calendar the
        // event is in, so no second call is needed to say where it lives. A
        // calendar with no title of its own — the personal one, named after
        // its owner elsewhere in the UI — adds nothing rather than a blank.
        const exoCalendarName = remoteEvent && remoteEvent.calendar && remoteEvent.calendar.title;
        if (exoCalendarName) {
          return this.$t('agenda.contemporaryEvents.rowExoCalendar', {0: remoteEvent.summary || '', 1: exoCalendarName});
        }
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
