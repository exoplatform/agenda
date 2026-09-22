<template>
  <v-app class="agenda-application" flat>
    <v-hover v-model="$root.hover">
      <v-card class="d-flex flex-column application-body position-static border-box-sizing" flat>
        <agenda-timeline-header
          :current-space="currentSpace"
          :events-count="displayedEvent.length"
          :calendars="calendars"
          :can-create-event="canCreateEvent"
          :agenda-base-link="agendaBaseLink"
          :connectors="enabledConnectors"
          :settings="settings"
          :period-title="periodTitle"
          :show-default-remote-events="showDefaultRemoteEvents" />
        <!-- a widget is glanced at rather than worked in: one short line is
             what a glance can carry, so it says the list is short of a source
             and leaves which account to the tooltip -->
        <div
          v-if="hasFailedSource"
          :title="failedSourceTitle"
          class="d-flex align-center px-4 pt-2 text-caption warning--text">
          <v-icon size="14" class="me-1 warning--text">
            fa-exclamation-triangle
          </v-icon>
          <span class="text-truncate">{{ failedSourceMessage }}</span>
        </div>
        <agenda-timeline
          v-if="$root.isTimelineView"
          :events="displayedEvent"
          :period-start-date="periodStart"
          :agenda-base-link="agendaBaseLink"
          :can-create-event="canCreateEvent"
          :loading="loading || !initialized"
          :limit="limit"
          :connected-connector="connectedConnector" />
        <agenda-body
          v-else
          :events="displayedEvent"
          :calendars="calendars"
          :calendar-type="calendarType"
          :weekdays="weekdays"
          :full-weekdays="fullWeekdays"
          :working-time="workingTime"
          :connected-connector="connectedConnector" />  
      </v-card>
    </v-hover>
    <agenda-event-dialog
      ref="eventFormDialog"
      :current-space="currentSpace"
      :calendars="calendars"
      :settings="settings"
      :connectors="enabledConnectors"
      :conference-provider="conferenceProvider"
      :weekdays="weekdays"
      :working-time="workingTime" />
    <agenda-event-quick-form-drawer
      :current-space="currentSpace"
      :calendars="calendars"
      :settings="settings"
      :conference-provider="conferenceProvider" />
    <agenda-event-save />
    <agenda-connector
      :settings="settings"
      :connectors="connectors"
      @connectors-loaded="connectors = $event" />
    <agenda-pending-invitation-drawer :current-space="currentSpace" />
    <agenda-connectors-drawer :connectors="connectors" />
    <agenda-timeline-settings-drawer />
  </v-app>
</template>
<script>
/**
 * How far ahead an account is read when it cannot answer "the next few" and
 * this view has no period. The window this widget has always asked for, kept
 * for the connectors that still need one: they read one endpoint rather than
 * one per calendar, so the horizon does not multiply for them as it did here.
 * That is the whole claim — nothing about what a year costs them.
 */
const REMOTE_EVENTS_FALLBACK_HORIZON_DAYS = 365;

/** How many events to ask for when the view states no limit of its own. */
const REMOTE_EVENTS_DEFAULT_TARGET = 10;

export default {
  data: () => ({
    initialized: false,
    currentSpace: null,
    calendars: [],
    /*
     * The two halves of a refresh, kept apart. They run concurrently and
     * finish in either order, so one flag written by both reported the page
     * loaded as soon as the faster half returned — normally the local store,
     * while the remote accounts were still being read.
     */
    loadingStore: false,
    loadingRemote: false,
    ownerIds: [],
    connectors: [],
    displayedEvent: [],
    periodStart: new Date(),
    period: {
      start: new Date(),
      end: null,
    },
    settings: {
      agendaDefaultView: 'week',
      agendaWeekStartOn: 'MO',
      showWorkingTime: false,
      workingTimeStart: '08:00',
      workingTimeEnd: '18:00',
    },
    events: [],
    remoteEvents: [],
    /*
     * The accounts whose last read failed. Kept apart from the events because
     * an account that could not answer is not an account that answered
     * "nothing": the widget would otherwise show a shorter list with nothing
     * saying a source dropped out.
     */
    failedConnectors: [],
    agendaBaseLink: null,
    conferenceProviders: null,
    selectedProviderType: null,
    remoteEventsLoaded: false,
    calendarType: 'month',
    periodTitle: '',
  }),
  computed: {
    /**
     * Whether either half of a refresh is still running. The page reports
     * itself loaded only once both have returned.
     *
     * @returns {Boolean} true while the store or the remote accounts are read
     */
    loading() {
      return this.loadingStore || this.loadingRemote;
    },
    /**
     * The accounts the widget could not read, named as the user knows them.
     *
     * @returns {Array} one display name per unreachable account
     */
    failedSourceNames() {
      return this.$agendaUtils.failedSourceNames(this.failedConnectors);
    },
    /**
     * Whether the list is showing less than it should.
     *
     * @returns {Boolean} true when at least one account could not be read
     */
    hasFailedSource() {
      return this.failedSourceNames.length > 0;
    },
    /**
     * What the line above the list says. A widget is glanced at, not worked
     * in, so it says only the one thing a glance has to carry — that the list
     * is short of a source — and leaves naming which one to its tooltip.
     *
     * @returns {String} the short warning line
     */
    failedSourceMessage() {
      return this.$t('agenda.timeline.someEventsMissing');
    },
    /**
     * Which accounts the short line is about, for the reader who stops on it.
     *
     * @returns {String} the tooltip naming the unread accounts
     */
    failedSourceTitle() {
      return this.$t('agenda.timeline.someEventsMissingTooltip', {
        0: this.failedSourceNames.join(', '),
      });
    },
    canCreateEvent() {
      return (this.$root?.timelineSettings && this.$root.timelineSettings.agendaSource !== 'selectedSpaces') || (this.calendars?.length && this.calendars.some(c => c?.acl?.canCreate));
    },
    enabledConferenceProviderName() {
      return this.settings
              && this.conferenceProviders
              && this.conferenceProviders.length > 0
              && this.conferenceProviders.find((provider) => provider.configured);
    },
    conferenceProvider() {
      return this.conferenceProviders && this.enabledConferenceProviderName && this.conferenceProviders.find(provider => provider.isInitialized && provider.linkSupported && provider.groupSupported && this.enabledConferenceProviderName.getType() === provider.getType());
    },
    weekdays() {
      return this.settings && this.$agendaUtils.getWeekSequenceFromDay(this.settings);
    },
    workingTime() {
      return this.settings && {
        showWorkingTime: this.settings.showWorkingTime,
        workingTimeStart: this.settings.workingTimeStart,
        workingTimeEnd: this.settings.workingTimeEnd
      };
    },
    fullWeekdays() {
      return this.settings && this.$agendaUtils.getWeekSequenceFromDay(this.settings, this.calendarType, true);
    },
    enabledConnectors() {
      return this.connectors && this.connectors.filter(connector => connector.initialized && connector.enabled) || [];
    },
    /**
     * The first connected connector, kept as the fallback identity for a
     * remote event that does not carry its own connector — each fetched event
     * is tagged with the account it came from, this is only the last resort.
     *
     * @returns {Object} the first connected connector, or undefined
     */
    connectedConnector() {
      return this.connectors && this.connectors.find(connector => connector.connected);
    },
    /**
     * Every connected connector: the user may hold one CalDAV account plus
     * one or more remote accounts at the same time.
     *
     * @returns {Array} the connected connectors, possibly empty
     */
    connectedConnectors() {
      return this.connectors && this.connectors.filter(connector => connector.connected) || [];
    },
    /**
     * The connected connectors whose browser session can be asked for remote
     * events right now.
     *
     * @returns {Array} the signed-in connected connectors
     */
    signedInConnectors() {
      return this.connectedConnectors.filter(connector => connector.isSignedIn);
    },
    /**
     * Whether at least one account can serve remote events.
     *
     * @returns {Boolean} true when a signed-in connected connector exists
     */
    signedInConnector() {
      return this.signedInConnectors.length > 0;
    },
    /**
     * The set of accounts to fetch from, as a comparable string, so the
     * watcher fires when an account joins or leaves rather than on every
     * mutation of the connector objects.
     *
     * @returns {String} the signed-in connector names, joined
     */
    signedInConnectorNames() {
      return this.signedInConnectors.map(connector => connector.name).join(',');
    },
    connectorStatus() {
      if (this.connectedConnectors.length) {
        if (this.signedInConnectors.length) {
          return 1;
        } else {
          return 2;
        }
      } else {
        return 0;
      }
    },

    showDefaultRemoteEvents() {
      return this.settings && this.settings.showRemoteEventsForTimeLine;
    },
    limit() {
      return !this.$root.isTimelineView ? null : this.$root.timelineSettings?.itemsNumber ? this.$root.timelineSettings.itemsNumber : 10;
    },
  },
  watch: {
    /**
     * The remote half is re-read too: how much is asked of the accounts
     * derives from how many items the view shows. Only while the timeline is
     * rendering — that is the view whose read is bounded by the count.
     * @returns {void}
     */
    limit() {
      this.retrieveEvents();
      this.refreshRemoteEvents();
    },
    initialized() {
      if (this.initialized) {
        this.$root.$emit('agenda-application-loaded');
      }
    },
    period() {
      this.events = [];
      this.retrieveEvents();
      if (this.settings.showRemoteEventsForTimeLine && this.signedInConnector) {
        this.retrieveRemoteEvents();
      }
    },
    loading() {
      if (this.loading) {
        document.dispatchEvent(new CustomEvent('displayTopBarLoading'));
      } else {
        document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
        this.$root.$applicationLoaded();
      }
    },
    /**
     * Fetches again when the set of accounts able to serve remote events
     * changes — one signing in must add its events, one signing out must take
     * them away — watched as a name list so connector-object mutations do not
     * refetch for nothing.
     * @returns {void}
     */
    signedInConnectorNames() {
      if (this.settings.showRemoteEventsForTimeLine) {
        this.retrieveRemoteEvents();
      }
    },
    events: {
      handler: 'updateDisplayedEvents',
      deep: true,
      immediate: true
    },
    remoteEvents: {
      handler: 'updateDisplayedEvents',
      deep: true
    },
    'showDefaultRemoteEvents': 'updateDisplayedEvents'
  },
  created() {
    this.$root.$on('agenda-change-period', period => {
      this.period = period;
      this.periodTitle = this.generateCalendarTitle(period);
    });
    this.retrieveEvents().finally(() => document.dispatchEvent(new CustomEvent('hideTopBarLoading')));
    this.$root.$on('agenda-settings-refresh', this.initSettings);
    this.$root.$on('agenda-refresh', this.retrieveEvents);
    this.$root.$on('agenda-event-saved', this.retrieveEvents);
    this.$root.$on('agenda-event-deleted', this.retrieveEvents);
    this.$root.$on('agenda-event-change-owner', this.refreshProviders);
    this.$root.$on('agenda-show-remote-change', this.showRemoteEvents);
    this.$root.$on('timeline-settings-updated', this.retrieveEvents);
    this.spaceId = eXo.env.portal.spaceId;
    // Asynchronously load settings to use it in dialogs,
    // not needed for main screen display
    this.initSettings();
  },
  methods: {
    initSettings(userSettings) {
      if (userSettings) {
        this.settings = userSettings;
      } else {
        return this.$settingsService.getUserSettings()
          .then(settings => {
            if (settings) {
              this.settings = settings;
              this.refreshProviders(eXo.env.portal.spaceName);
            }
          })
          .finally(() => {
            this.settingsLoaded = true;
          });
      }
    },
    updateDisplayedEvents() {
      if (this.showDefaultRemoteEvents) {
        // Avoid to have same event from remote and local store (pushed events from local store)
        const filtered = this.filterRemoteEvents(this.events, this.remoteEvents);
        const merged = [...this.events, ...filtered];
        merged.sort((a, b) => {
          const s1 = this.$agendaUtils.toDate(a.start || a.startDate).getTime();
          const s2 = this.$agendaUtils.toDate(b.start || b.startDate).getTime();
          return s1 - s2;
        });
        this.displayedEvent = merged;
      } else {
        this.displayedEvent = [...this.events];
      }
    },
    generateCalendarTitle(period) {
      return this.$agendaUtils.generateCalendarTitle(this.calendarType, this.$agendaUtils.toDate(period.start), period.title, this.$t('agenda.week'));
    },
    refreshProviders(spacePrettyName) {
      if (spacePrettyName) {
        this.$webConferencingService.getAllProviders(spacePrettyName).then((providers) => {
          this.conferenceProviders = providers;
          return this.$nextTick();
        });
      } else {
        this.conferenceProviders = null;
      }
    },
    showRemoteEvents(showRemoteEvents) {
      if (this.settings.showRemoteEventsForTimeLine !== showRemoteEvents){
        this.settings.showRemoteEventsForTimeLine = showRemoteEvents;
        this.$settingsService.saveUserSettings(this.settings);
        if (showRemoteEvents && !this.remoteEventsLoaded) {
          this.retrieveRemoteEvents();
        }
      }
    },
    async retrieveEvents() {
      if (this.$root.timelineSettings.agendaSource === 'selectedSpaces') {
        this.ownerIds = this.$root.timelineSettings.selectedSpaces.map(space => space.identityId).filter(id => !!id);
        this.retrieveEventsFromStore();
        const results = await Promise.allSettled(
          this.ownerIds.map(ownerId =>
            this.$calendarService.getCalendars(0, 1, false, [ownerId])
          )
        );
        const calendars = results
          .filter(result => result.status === 'fulfilled')
          .flatMap(result => result.value?.calendars || []);
        this.calendars = calendars;
      } else if (this.$root.timelineSettings.agendaSource === 'allUsersSpaces'){
        this.ownerIds = [];
        return this.retrieveEventsFromStore();
      } else if (!this.initialized && eXo.env.portal.spaceId && !this.$root.standalone) {
        const spaceId = eXo.env.portal.spaceId;
        return this.$spaceService.getSpaceById(spaceId, 'identity')
          .then((space) => {
            this.currentSpace = space;
            if (space && space.identity && space.identity.id) {
              this.ownerIds = [space.identity.id];
              this.agendaBaseLink = `${eXo.env.portal.context}/s/${eXo.env.portal.spaceId}/agenda`;
              return this.$calendarService.getCalendars(0, 1, false, this.ownerIds);
            } else {
              this.agendaBaseLink = `${eXo.env.portal.context}/${eXo.env.portal.portalName}/agenda`;
            }
          })
          .then(data => {
            this.calendars = data && data.calendars && data.calendars.length && data.calendars || [];
          })
          .finally(() => {
            this.initialized = true;
            this.loadingStore = false;
            this.retrieveEventsFromStore();
          });
      } else {
        if (!eXo.env.portal.spaceId || this.$root.standalone) {
          this.ownerIds = [];
          this.agendaBaseLink = `${eXo.env.portal.context}/${eXo.env.portal.portalName}/agenda`;
        }
        return this.retrieveEventsFromStore();
      }
    },
    retrieveEventsFromStore() {
      this.loadingStore = true;
      let agendaFilter = this.$root.timelineSettings.agendaFilter;
      if (!agendaFilter) {
        if (eXo.env.portal.spaceId && !this.$root.standalone) {
          agendaFilter = 'allEvents';
        } else {
          agendaFilter = 'acceptedEvents';
        }
      }
      const userIdentityId = agendaFilter === 'acceptedEvents' && eXo.env.portal.userIdentityId || null;
      const responseTypes = agendaFilter === 'acceptedEvents' ? ['ACCEPTED'] : ['ACCEPTED', 'NEEDS_ACTION', 'TENTATIVE'];
      return this.$eventService.getEvents(this.searchTerm, this.ownerIds, userIdentityId, this.$agendaUtils.toRFC3339(this.period.start, false), this.$agendaUtils.toRFC3339(this.period.end), this.limit, responseTypes, 'attendees,conferences')
        .then(data => {
          const events = data && data.events || [];
          events.forEach(event => {
            event.name = event.summary;
            event.startDate = event.start && this.$agendaUtils.toDate(event.start) || null;
            event.endDate = event.end && this.$agendaUtils.toDate(event.end) || null;
          });
          this.events = events;
        }).catch(error =>{
          console.error('Error retrieving events', error);
        }).finally(() => {
          this.initialized = true;
          this.loadingStore = false;
        });
    },
    /**
     * Re-reads the remote accounts, when the user asked to see them at all.
     *
     * @returns {void}
     */
    refreshRemoteEvents() {
      // Only where the count is what the read is bounded by. bodyElementWidth
      // starts at 0, so the first render is always the timeline and flips on
      // the ResizeObserver's first delivery; without this guard that flip
      // fired a full remote read the period watcher immediately superseded.
      if (this.settings.showRemoteEventsForTimeLine && this.$root.isTimelineView) {
        this.retrieveRemoteEvents();
      }
    },
    /**
     * The end of a remote read starting at the displayed period's start.
     *
     * @param {Number} days how far ahead to look
     * @returns {Date} the end of the window
     */
    remoteHorizonEnd(days) {
      const end = new Date(this.period.start);
      end.setDate(end.getDate() + days);
      return end;
    },
    /**
     * Fetches the remote events of every signed-in connected account for the
     * displayed period and merges them into one deduplicated array, each
     * event tagged with the account it came from.
     *
     * While the timeline is rendering, this widget receives no period:
     * agenda-change-period is emitted by AgendaCalendar alone, which lives in
     * the agenda-body branch this widget renders only above the sm threshold.
     * So in the timeline the fallback window was the only one ever used — a
     * year of it, per calendar per account, to fill ten items (EXO-90496).
     *
     * An account that can answer "the next few events" is asked that, so no
     * horizon is invented and nothing is lost however far off the next
     * meeting is. One that cannot keeps its usual window.
     *
     * @returns {Promise} resolves once the remote events are in hand
     */
    async retrieveRemoteEvents() {
      if (this.connectorStatus !== 1) {
        this.remoteEvents = [];
        this.failedConnectors = [];
        return;
      }
      this.loadingRemote = true;
      try {
        const sources = await this.readRemoteEvents();
        this.remoteEvents = sources.events;
        this.failedConnectors = sources.failedConnectors;
        this.remoteEventsLoaded = true;
      } finally {
        // The page waits on this to stop showing itself as loading; leaving
        // it raised on a failed read is the defect this change is about.
        this.loadingRemote = false;
      }
    },
    /**
     * Asks one account in whichever way it can answer: the next `wanted`
     * events when the timeline is rendering and the account knows how, the
     * period otherwise.
     *
     * The test is which view is rendering, not whether a period is held.
     * `period` has one writer — the agenda-change-period handler — and is
     * never cleared, so a session that rendered agenda-body once keeps that
     * period for the rest of the page's life; testing it would pin the
     * timeline to a stale calendar window the moment the widget is narrowed
     * back, which is exactly the horizon this change removes.
     *
     * The count bounds the events asked for; the list truncates at `limit`
     * entries and a multi-day event occupies one per day, so this asks for
     * enough rather than for exactly what is shown.
     *
     * @param {Object} connector the account to read
     * @param {String} startDateRFC3359 the date to read from
     * @param {Number} wanted how many events the view can show
     * @returns {Promise} resolves with that account's answer
     */
    readOneConnector(connector, startDateRFC3359, wanted) {
      if (this.$root.isTimelineView) {
        if (typeof connector.getUpcomingEvents === 'function') {
          return connector.getUpcomingEvents(startDateRFC3359, wanted);
        }
        return connector.getEvents(startDateRFC3359, this.remoteHorizon());
      }
      const endDate = this.period.end || this.remoteHorizonEnd(REMOTE_EVENTS_FALLBACK_HORIZON_DAYS);
      return connector.getEvents(startDateRFC3359, this.$agendaUtils.toRFC3339(endDate, false, true));
    },
    /**
     * The fallback window's end, as the connectors take it.
     *
     * @returns {String} an RFC3339 timestamp
     */
    remoteHorizon() {
      return this.$agendaUtils.toRFC3339(this.remoteHorizonEnd(REMOTE_EVENTS_FALLBACK_HORIZON_DAYS), false, true);
    },
    /**
     * Asks every signed-in account for its events.
     *
     * @returns {Promise} resolves with {events, failedConnectors}
     */
    readRemoteEvents() {
      const startDateRFC3359 = this.$agendaUtils.toRFC3339(this.period.start, false, true);
      const wanted = this.limit || REMOTE_EVENTS_DEFAULT_TARGET;
      // Each account fails on its own: one unreachable must not blank the
      // others. Wrapped so a connector answering anything but a promise is
      // one failed source, not a read that never returns.
      return Promise.all(this.signedInConnectors.map(connector =>
        Promise.resolve()
          .then(() => this.readOneConnector(connector, startDateRFC3359, wanted))
          .then(answer => {
            // A connector may report a partial read: the events it did get,
            // beside the fact that it could not get all of them. Both halves
            // travel on, or the view draws a short week as a whole one.
            const read = this.$agendaUtils.readConnectorAnswer(answer);
            read.events.forEach(event => {
              event.startDate = event.start && this.$agendaUtils.toDate(event.start) || null;
              event.endDate = event.end && this.$agendaUtils.toDate(event.end) || null;
            });
            return {connector, events: read.events, failed: read.failed};
          })
          .catch(error => {
            console.error('Error retrieving remote events', connector.name, error);
            // No events array at all: an account that could not answer must
            // not reach the widget as an account that answered "nothing",
            // which would shorten the list with nothing saying why.
            return {connector, failed: true};
          })))
        .then(eventsByConnector => this.$agendaUtils.splitRemoteEventResults(eventsByConnector));
    },
    filterRemoteEvents(localEvents, remoteEvents) {
      return remoteEvents.filter(remote => {
        const isMatched = localEvents.some(local => {
          const sameId = remote.id === local.remoteId;
          const sameDates =  new Date(remote.startDate).getTime() === new Date(local.startDate).getTime() && new Date(remote.endDate).getTime() === new Date(local.endDate).getTime();
          const sameRecurring = remote.recurringEventId === local.parent?.remoteId;
          return sameId || (sameRecurring && sameDates);
        });
        return !isMatched;
      });
    }
  },
};
</script>
