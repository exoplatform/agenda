<template>
  <v-app class="agenda-application border-box-sizing" flat>
    <template v-if="settingsLoaded && !hideApp">
      <v-main class="application-body" :class="$root.isMobile ? 'pt-2 px-1' : ''">
        <agenda-header
          :calendar-type="calendarType"
          :event-type="eventType"
          :current-space="currentSpace"
          :current-calendar="currentCalendar"
          :owner-ids="ownerIds"
          :period-title="periodTitle"
          :settings="settings"
          :period="period"
          :connectors="enabledConnectors" />
        <agenda-timeline
          v-if="$root.isMobile"
          :events="displayedEvent"
          :period-start-date="period.start"
          :loading="loading"
          :limit="limit"
          :connected-connector="connectedConnector"
          class="mt-2 pa-5" />
        <v-row
          v-else
          class="agenda-desktop-body flex-nowrap"
          no-gutters>
          <v-col
            v-show="displayLeftPanel"
            v-if="leftPanelAvailable"
            class="agenda-left-panel-column flex-grow-0 flex-shrink-0">
            <agenda-left-panel
              :selected-owner-ids="ownerIds"
              :expanded="displayLeftPanel"
              :connectors="enabledConnectors"
              :settings="settings"
              :period="period" />
          </v-col>
          <v-col class="agenda-body-column">
            <agenda-body
              :events="displayedEvent"
              :calendars="[currentCalendar]"
              :calendar-type="calendarType"
              :weekdays="weekdays"
              :full-weekdays="fullWeekdays"
              :working-time="workingTime"
              :connected-connector="connectedConnector" />
          </v-col>
        </v-row>
        <v-flex v-if="$root.isMobile && hasMore" class="d-flex py-4 border-box-sizing">
          <v-btn
            :loading="loading"
            :disabled="loading"
            class="btn mx-auto"
            @click="limit += pageSize">
            {{ $t('agenda.button.loadMore') }}
          </v-btn>
        </v-flex>
      </v-main>
    </template>

    <agenda-event-dialog
      ref="eventFormDialog"
      :current-space="currentSpace"
      :calendars="[currentCalendar]"
      :settings="settings"
      :connectors="enabledConnectors"
      :conference-provider="conferenceProvider"
      :weekdays="fullWeekdays"
      :working-time="workingTime" />
    <agenda-event-preview-dialog />
    <agenda-filter-calendar-drawer
      :current-space="currentSpace"
      :settings="settings"
      :calendar-type="calendarType" />
    <agenda-user-setting-drawer :settings="settings" />
    <agenda-event-quick-form-drawer
      :current-space="currentSpace"
      :calendars="[currentCalendar]"
      :settings="settings"
      :conference-provider="conferenceProvider" />
    <agenda-event-save />
    <agenda-connector
      :settings="settings"
      :connectors="connectors"
      @connectors-loaded="connectors = $event" />
    <agenda-notification-alerts />
    <agenda-events-updater
      v-if="settingsLoaded"
      :settings="settings"
      :current-calendar="currentCalendar"
      :calendar-type="eventType"
      :owner-ids="effectiveOwnerIds"
      :events="events"
      :period="period"
      :limit="limit" />
    <agenda-pending-invitation-drawer :current-space="currentSpace" />
    <agenda-connectors-drawer :connectors="connectors" />
    <!--
      Mounted here rather than inside the connectors drawer. exo-drawer renders
      its content only when first opened, so a component living inside one does
      not exist — and cannot listen for anything — until the user has opened
      that drawer at least once. The event asking for this step fires straight
      after connecting, when they have not, so the step was never offered.
    -->
    <agenda-connector-mirror-calendar-drawer />
    <!--
      One single instance for the whole application: the personal calendar
      list renders both in the left panel and in the mobile filter drawer, and
      both open this drawer through the same root event — two instances would
      open two drawers.
    -->
    <agenda-personal-calendar-drawer />
  </v-app>
</template>
<script>
export default {
  props: {
    eventType: {
      type: String,
      // allEvents, declinedEvent or myEvents
      default: () => 'myEvents',
    },
    hideApp: {
      type: Boolean,
      default: false,
    },
    standalone: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    initialized: false,
    currentCalendar: null,
    currentSpace: null,
    filterCanceledEvents: true,
    loading: false,
    conferenceProviders: null,
    connectors: [],
    ownerIds: [],
    searchTerm: null,
    periodTitle: '',
    calendarType: 'week',
    pageSize: 10,
    limit: 0,
    period: {
      start: new Date(),
      end: null,
    },
    events: [],
    remoteEvents: [],
    hiddenRemoteCalendarIds: [],
    hiddenPersonalCalendarIds: [],
    displayedEvent: [],
    settings: {
      agendaDefaultView: 'week',
      agendaWeekStartOn: 'MO',
      showWorkingTime: false,
      workingTimeStart: '08:00',
      workingTimeEnd: '18:00',
    },
    hasMore: false,
    settingsLoaded: false,
    // Monotonic token identifying the latest events request: responses of
    // superseded requests are discarded instead of overwriting fresher ones
    eventsRequestId: 0,
    leftPanelExpanded: localStorage.getItem('agendaLeftPanelExpanded') !== 'false',
  }),
  computed: {
    spaceContextId() {
      return !this.standalone && eXo.env.portal.spaceId || null;
    },
    /**
     * Whether the left panel feature is available in the current context: it
     * is a personal agenda feature, thus hidden inside a space agenda where a
     * single space calendar is displayed.
     *
     * @returns {boolean} true when the left panel can be displayed
     */
    leftPanelAvailable() {
      return !this.spaceContextId;
    },
    /**
     * The owner identity ids effectively asked of the events REST.
     *
     * The Spaces section of the left panel selects SPACES, and its selection
     * used to travel to the server untouched — so ticking one space asked for
     * that space's owner and nothing else, and every personal calendar
     * silently left the grid (EXO-89818). They were never hidden by a display
     * rule: they were not asked for. The user's own identity is therefore
     * always part of the request whenever a space subset is selected, their
     * personal calendars being selected one by one through excludedCalendarIds
     * instead — a space calendar has its own owner, a personal calendar shares
     * the user's, and the two cannot be expressed by the same list.
     *
     * A space agenda is left untouched: there is no left panel there, the
     * selection is the space itself, and adding the viewer's own calendars
     * would put personal events on a space's page.
     *
     * @returns {Array|boolean} owner identity ids to request: an empty array
     *          means every accessible calendar
     */
    effectiveOwnerIds() {
      if (!this.leftPanelAvailable) {
        return this.ownerIds;
      }
      const userIdentityId = Number(eXo.env.portal.userIdentityId);
      if (this.ownerIds === false) {
        // No space selected: the user's own calendars remain, which of them
        // is displayed being the personal list's decision, not this one
        return [userIdentityId];
      }
      if (!Array.isArray(this.ownerIds) || !this.ownerIds.length) {
        return this.ownerIds;
      }
      const selectedOwnerIds = this.ownerIds.map(Number);
      return selectedOwnerIds.includes(userIdentityId) ? selectedOwnerIds : selectedOwnerIds.concat(userIdentityId);
    },
    /**
     * Whether the left panel is effectively displayed: available in the
     * current context and not collapsed by the user.
     *
     * @returns {boolean} true when the left panel is displayed
     */
    displayLeftPanel() {
      return this.leftPanelAvailable && this.leftPanelExpanded;
    },
    enabledConferenceProviderName() {
      return this.settings
          && this.conferenceProviders
          && this.conferenceProviders.length > 0
          && this.conferenceProviders.find((provider) => provider.isInitialized && provider.configured);
    },
    conferenceProvider() {
      return this.conferenceProviders && this.enabledConferenceProviderName && this.conferenceProviders.find(provider => provider.isInitialized && provider.linkSupported && provider.groupSupported && this.enabledConferenceProviderName.getType() === provider.getType());
    },
    enabledConnectors() {
      return this.connectors && this.connectors.filter(connector => connector.initialized && connector.enabled) || [];
    },
    /**
     * The first connected connector, kept as the fallback identity for a
     * remote event that does not carry its own connector — with several
     * accounts connected, each fetched event is tagged with the account it
     * came from and this is only the last resort.
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
    weekdays() {
      return this.settings && this.$agendaUtils.getWeekSequenceFromDay(this.settings, this.calendarType, false);
    },
    fullWeekdays() {
      return this.settings && this.$agendaUtils.getWeekSequenceFromDay(this.settings, this.calendarType, true);
    },
    workingTime() {
      return this.settings && {
        showWorkingTime: this.settings.showWorkingTime,
        workingTimeStart: this.settings.workingTimeStart,
        workingTimeEnd: this.settings.workingTimeEnd,
        workedDaysNumber: this.settings.workedDaysNumber,
      };
    },
  },
  watch: {
    /**
     * Drops the mobile page size when the application leaves the mobile
     * layout. Only the mobile timeline paginates: it asks for one page from
     * its created() hook, and that ask puts an explicit `limit` on the events
     * query — which the server honours even when a period end date is set.
     * The desktop grid has no pagination and must query the whole period, so
     * a `limit` inherited from a mobile phase would silently cap the grid at
     * one page until the page is reloaded (EXO-89791).
     *
     * Resetting here removes the coupling rather than narrowing the race the
     * unmeasured-width fix in main.js closes: no future transient mobile
     * phase can leave a page size behind on a desktop query.
     *
     * @param {Boolean} isMobile the layout the application switched to
     * @returns {void}
     */
    '$root.isMobile'(isMobile) {
      if (!isMobile) {
        this.limit = 0;
      }
    },
    limit() {
      this.retrieveEvents();
    },
    eventType() {
      this.retrieveEvents();
    },
    initialized() {
      if (this.initialized) {
        this.$root.$emit('agenda-application-loaded');
      }
    },
    /**
     * Reloads both event sources when the displayed period moves: the local
     * store always, the remote accounts when one can answer. Remote events
     * are no longer gated by a setting: connecting an account is the opt-in,
     * and the per-calendar checkboxes of the left panel decide what is shown.
     * @returns {void}
     */
    period() {
      this.events = [];
      this.retrieveEvents();
      if (this.signedInConnector) {
        this.retrieveRemoteEvents();
      }
    },
    searchTerm() {
      this.retrieveEvents();
    },
    loading() {
      if (this.loading) {
        document.dispatchEvent(new CustomEvent('displayTopBarLoading'));
      } else {
        this.$root.$applicationLoaded();
        document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
      }
    },
    /**
     * Fetches again when the set of accounts able to serve remote events
     * changes — one signing in must add its events, one signing out must take
     * them away — watched as a name list so connector-object mutations do not
     * refetch for nothing. retrieveRemoteEvents itself empties the remote
     * events when no account can serve any, which is what takes a signed-out
     * account's events off the grid.
     * @returns {void}
     */
    signedInConnectorNames() {
      this.retrieveRemoteEvents();
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
    /**
     * Reflects a personal-calendar visibility change twice: on the events
     * already in hand, so the grid answers the click at once, and on the
     * server, which is now told which calendars to leave out. The request
     * matters beyond tidiness — the events of a hidden calendar used to be
     * counted against the requested `limit` and only dropped afterwards, so
     * hiding a calendar quietly returned a short page.
     * @returns {void}
     */
    hiddenPersonalCalendarIds() {
      this.updateDisplayedEvents();
      if (this.initialized) {
        // Restoring the persisted selection at startup must not fire a second
        // query: the first one has not been issued yet and already carries it
        this.retrieveEvents();
      }
    },
  },
  created() {
    // Ensure that localStorage doesn't have a deleted event
    window.setTimeout(() => {
      localStorage.removeItem('agendaDeletedEvents');
    }, 10000);

    this.$root.$on('agenda-change-period', period => {
      this.period = period;
      this.periodTitle = this.generateCalendarTitle(period);
    });
    this.$root.$on('agenda-event-limit-increment', () => this.limit += this.pageSize);
    this.$root.$on('agenda-change-period-type', calendarType => this.calendarType = calendarType);
    this.$root.$on('agenda-search', searchTerm => this.searchTerm = searchTerm);
    this.$root.$on('agenda-event-saved', this.savedEvent);
    this.$root.$on('agenda-refresh', this.retrieveEvents);
    this.$root.$on('agenda-event-type-changed', eventType => this.eventType = eventType);
    this.$root.$on('agenda-event-deleted', this.deletedEvent);
    this.$root.$on('agenda-event-response-sent', this.retrieveEvents);
    this.spaceId = this.spaceContextId;
    this.$root.$on('agenda-calendar-owners-changed', this.changeDisplayedOwnerIds);
    this.$root.$on('agenda-left-panel-toggle', this.toggleLeftPanel);
    this.$root.$on('agenda-remote-calendars-changed', this.changeHiddenRemoteCalendars);
    this.$root.$on('agenda-personal-calendars-visibility-changed', this.changeHiddenPersonalCalendars);
    this.initHiddenPersonalCalendars();
    this.$root.$on('agenda-settings-refresh', this.initSettings);
    this.$root.$on('agenda-event-change-owner', this.refreshProviders);
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
              this.calendarType = this.settings && this.settings.agendaDefaultView;
              this.refreshProviders(eXo.env.portal.spaceName);
            }
          })
          .finally(() => {
            this.settingsLoaded = true;
          });
      }
    },
    refreshProviders(spacePrettyName) {
      if (spacePrettyName) {
        this.$webConferencingService.getAllProviders(spacePrettyName).then(providers => {
          this.conferenceProviders = providers;
          return this.$nextTick();
        });
      } else {
        this.conferenceProviders = null;
      }
    },
    /**
     * Rebuilds the displayed events from the two sources in hand: the local
     * store filtered by the personal-calendar visibility, and the remote
     * events filtered by the per-calendar checkboxes of the left panel. What
     * is shown is decided per calendar and nowhere else — the global
     * show-remote setting this method used to read is retired, connecting an
     * account being the opt-in.
     *
     * @returns {void}
     */
    updateDisplayedEvents() {
      // The server already leaves the hidden personal calendars out — it is
      // told which ones through excludedCalendarIds. This repeats the filter
      // for the events that never went through that query: the websocket
      // updater pushes a created or updated event straight into this store.
      // It stays scoped to calendars owned by the current user so that a
      // space calendar can never be hidden by it, whatever the persisted
      // list contains
      const userIdentityId = Number(eXo.env.portal.userIdentityId);
      const localEvents = this.events.filter(event => !event.calendar
          || !event.calendar.owner
          || Number(event.calendar.owner.id) !== userIdentityId
          || !this.hiddenPersonalCalendarIds.includes(Number(event.calendar.id)));
      if (this.remoteEvents.length) {
        // Avoid to have same event from remote and local store (pushed events from local store)
        const filtered = this.filterRemoteEvents(this.events, this.remoteEvents)
          .filter(remote => !this.hiddenRemoteCalendarIds.includes(remote.calendarId));
        const merged = [...localEvents, ...filtered];
        merged.sort((a, b) => {
          const s1 = this.$agendaUtils.toDate(a.start || a.startDate).getTime();
          const s2 = this.$agendaUtils.toDate(b.start || b.startDate).getTime();
          return s1 - s2;
        });
        this.displayedEvent = merged;
      } else {
        this.displayedEvent = localEvents;
      }
    },
    retrieveEvents() {
      if (!this.initialized && this.spaceContextId) {
        const spaceId = this.spaceContextId;
        this.$spaceService.getSpaceById(spaceId, 'identity')
          .then((space) => {
            this.currentSpace = space;
            this.eventType = space ? 'allEvents' : 'myEvents';
            if (space && space.identity && space.identity.id) {
              this.ownerIds = [space.identity.id];
            }
            if (this.ownerIds && this.ownerIds.length) {
              return this.$calendarService.getCalendars(0, 1, false, this.ownerIds);
            }
          })
          .then(data => {
            this.currentCalendar = data && data.calendars && data.calendars.length && data.calendars[0] || null;
          })
          .finally(() => {
            this.retrieveEventsFromStore();
          });
      } else {
        this.retrieveEventsFromStore();
      }
    },
    /**
     * Retrieves the events matching the current selection (period, owners,
     * event type, search term) and displays them. Selection changes, saves
     * and period moves each trigger a retrieval, so several requests can be
     * in flight at once: each request takes a monotonic token, and a response
     * belonging to a superseded request is discarded — otherwise a slower
     * stale response landing last silently overwrites the fresher one, and
     * the grid drops events that the current selection does include.
     *
     * @returns {Promise|void} resolved when the events are displayed
     */
    retrieveEventsFromStore() {
      const requestId = ++this.eventsRequestId;
      this.loading = true;
      const userIdentityId = this.eventType !== 'allEvents' && eXo.env.portal.userIdentityId || null;
      if (this.effectiveOwnerIds === false) {
        this.events = [];
        this.hasMore = false;
        this.loading = false;
        this.initialized = true;
        return;
      }
      const responseTypes = this.spaceContextId && this.eventType === 'allEvents' ? null : this.eventType === 'declinedEvent' ? ['DECLINED']:['ACCEPTED', 'NEEDS_ACTION', 'TENTATIVE'];
      return this.$eventService.getEvents(this.searchTerm, this.effectiveOwnerIds, userIdentityId, this.$agendaUtils.toRFC3339(this.period.start, true), this.$agendaUtils.toRFC3339(this.period.end), this.limit, responseTypes, 'attendees,conferences', this.hiddenPersonalCalendarIds)
        .then(data => {
          if (requestId !== this.eventsRequestId) {
            // A newer retrieval was started since: its response is the one
            // reflecting the current selection, let it drive the display
            return;
          }
          let events = data && data.events || [];
          if (this.filterCanceledEvents) {
            events = events.filter(event => !event.status || event.status !== 'CANCELLED');
          }
          events.forEach(event => {
            event.name = event.summary;
            event.startDate = event.start && this.$agendaUtils.toDate(event.start) || null;
            event.endDate = event.end && this.$agendaUtils.toDate(event.end) || null;
          });
          this.hasMore = this.limit > this.pageSize && (this.events && this.events.length || 0) < events.length || events.length >= this.limit;
          this.events = events;
        }).catch(error =>{
          console.error('Error retrieving events', error);
        }).finally(() => {
          if (requestId === this.eventsRequestId) {
            this.initialized = true;
            this.loading = false;
          }
        });
    },
    generateCalendarTitle(period) {
      return this.$agendaUtils.generateCalendarTitle(this.calendarType, this.$agendaUtils.toDate(period.start), period.title, this.$t('agenda.week'));
    },
    /**
     * Applies a new calendar selection coming from the left panel or the
     * filter drawer, then reloads the displayed events accordingly.
     *
     * @param {Array|boolean} selectedOwnerIds selected calendar owner
     *          identity ids: an empty array means 'all calendars', false means
     *          'no calendar'
     * @returns {void}
     */
    changeDisplayedOwnerIds(selectedOwnerIds) {
      this.ownerIds = selectedOwnerIds;
      this.retrieveEvents();
    },
    /**
     * Collapses or expands the left panel, persists the choice in the browser
     * local storage, then dispatches a window resize event once the layout
     * settled: Vuetify's v-calendar measures its own width and would otherwise
     * keep rendering the week grid with a stale width after the panel changed
     * the available space.
     * @returns {void}
     */
    toggleLeftPanel() {
      this.leftPanelExpanded = !this.leftPanelExpanded;
      localStorage.setItem('agendaLeftPanelExpanded', String(this.leftPanelExpanded));
      this.$nextTick().then(() => window.dispatchEvent(new Event('resize')));
    },
    updateSettings(settings) {
      this.settings = settings;
    },
    savedEvent(event) {
      if (event && event.id) {
        const isDatePoll = event.status === 'TENTATIVE';
        const isNew = !event.updated;
        const message = isDatePoll && (isNew && this.$t('agenda.datePollCreationSuccess') || this.$t('agenda.datePollUpdateSuccess'))
                     || (isNew && this.$t('agenda.eventCreationSuccess') || this.$t('agenda.eventUpdateSuccess'));
        const clickMessage = isDatePoll && this.$t('agenda.viewDatePoll') || this.$t('agenda.viewEvent');
        document.dispatchEvent(new CustomEvent('alert-message', {detail: {
          alertType: 'success',
          alertMessage: message,
          alertLinkText: clickMessage,
          alertLinkCallback: () => this.$root.$emit('agenda-event-details', event),
        }}));
      }
    },
    deletedEvent(event, untilDateRecurrenceUpdated) {
      if (event && event.id) {
        const isDatePoll = event.status === 'TENTATIVE';
        const message = isDatePoll
                        && this.$t('agenda.datePollDeleteSuccess')
                        || (untilDateRecurrenceUpdated && this.$t('agenda.eventRecurrenceUntilDateUpdated'))
                        || this.$t('agenda.eventDeleteSuccess');     
        const deleteEventAlert = {detail: {
          alertType: 'success',
          alertMessage: message,
        }};
        if (isDatePoll || !untilDateRecurrenceUpdated) {
          deleteEventAlert.detail.alertLinkText = this.$t('agenda.undoRemoveEvent');
          deleteEventAlert.detail.alertLinkCallback = () => this.undoDeleteEvent(event);
        }
        document.dispatchEvent(new CustomEvent('alert-message', deleteEventAlert));
      }
    },
    undoDeleteEvent(event) {
      if (event.occurrence && event.occurrence.id) {
        return this.$eventService.updateEventFields(event, {status: 'CONFIRMED'}, false, true)
          .then(() => {
            this.$root.$emit('agenda-refresh', event);
            this.$root.$emit('alert-message', this.$t('agenda.eventDeletionCanceled'), 'success');
          });
      } else {
        return this.$eventService.undoDeleteEvent(event.id)
          .then(() => {
            this.$root.$emit('agenda-refresh', event);
            this.$root.$emit('alert-message', this.$t('agenda.eventDeletionCanceled'), 'success');
          });
      }
    },
    /**
     * Fetches the remote events of every signed-in connected account for the
     * displayed period and merges them into one deduplicated array, each
     * event tagged with the account it came from.
     *
     * @returns {void}
     */
    retrieveRemoteEvents() {
      // A connector can sign in before the calendar has emitted its first
      // period, in which case end is still null and the read goes out with a
      // half-built window that strict connectors (CalDAV) reject outright.
      // Skipping loses nothing: the period watcher retrieves again as soon as
      // the calendar has a real period.
      if (!this.period || !this.period.start || !this.period.end) {
        return;
      }
      if (this.settingsLoaded && this.connectorStatus === 1) {
        const startDateRFC3359 = this.$agendaUtils.toRFC3339(this.period.start, false, true);
        const endDateRFC3359 = this.$agendaUtils.toRFC3339(this.period.end, false, true);
        this.loading = true;
        // Every signed-in account is asked, and each fails on its own: one
        // unreachable account must not blank the events the others returned
        Promise.all(this.signedInConnectors.map(connector =>
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
              return {connector, events: []};
            })))
          .then(eventsByConnector => {
            this.remoteEvents = this.$agendaUtils.mergeRemoteEvents(eventsByConnector);
            this.loading = false;
          });
      } else {
        this.remoteEvents = [];
      }
    },
    /**
     * Records which remote calendars the user has hidden in the left panel and
     * redraws the grid, so unchecking one takes its events off the calendar
     * without asking the server again — the events are already in hand, only
     * the choice of what to show has changed.
     *
     * @param {Array} hiddenRemoteCalendarIds identifiers of the calendars to hide
     * @returns {void}
     */
    changeHiddenRemoteCalendars(hiddenRemoteCalendarIds) {
      this.hiddenRemoteCalendarIds = hiddenRemoteCalendarIds || [];
      this.updateDisplayedEvents();
    },
    /**
     * Applies a new personal-calendar visibility selection coming from the
     * personal calendar list (left panel or mobile filter drawer).
     *
     * @param {Array} hiddenPersonalCalendarIds identifiers of the personal
     *          calendars whose events must be hidden
     * @returns {void}
     */
    changeHiddenPersonalCalendars(hiddenPersonalCalendarIds) {
      this.hiddenPersonalCalendarIds = (hiddenPersonalCalendarIds || []).map(Number);
    },
    /**
     * Restores the personal-calendar visibility persisted in the browser
     * storage, so a hidden calendar stays hidden across reloads.
     * @returns {void}
     */
    initHiddenPersonalCalendars() {
      try {
        const storedValue = localStorage.getItem(`agenda.hiddenPersonalCalendars.${eXo.env.portal.userIdentityId}`);
        const hiddenIds = storedValue && JSON.parse(storedValue) || [];
        this.hiddenPersonalCalendarIds = Array.isArray(hiddenIds) ? hiddenIds.map(Number) : [];
      } catch (e) {
        this.hiddenPersonalCalendarIds = [];
      }
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
