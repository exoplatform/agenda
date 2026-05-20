<template>
  <v-app class="agenda-application" flat>
    <v-hover v-model="$root.hover">
      <v-card class="d-flex flex-column application-body position-static border-box-sizing" flat>
        <agenda-timeline-header
          :current-space="currentSpace"
          :current-calendar="currentCalendar"
          :agenda-base-link="agendaBaseLink"
          :connectors="enabledConnectors"
          :settings="settings"
          :period-title="periodTitle"
          :show-default-remote-events="showDefaultRemoteEvents" />
        <agenda-timeline
          v-if="$root.isTimelineView"
          :events="displayedEvent"
          :period-start-date="periodStart"
          :agenda-base-link="agendaBaseLink"
          :loading="loading || !initialized"
          :limit="limit"
          :connected-connector-avatar="connectedConnectorAvatar" />
        <agenda-body
          v-else
          :events="displayedEvent"
          :current-calendar="currentCalendar"
          :calendar-type="calendarType"
          :weekdays="weekdays"
          :full-weekdays="fullWeekdays"
          :working-time="workingTime"
          :connected-connector-avatar="connectedConnectorAvatar" />  
      </v-card>
    </v-hover>
    <agenda-event-dialog
      ref="eventFormDialog"
      :current-space="currentSpace"
      :current-calendar="currentCalendar"
      :settings="settings"
      :connectors="enabledConnectors"
      :conference-provider="conferenceProvider"
      :weekdays="weekdays"
      :working-time="workingTime" />
    <agenda-event-quick-form-drawer
      :current-space="currentSpace"
      :current-calendar="currentCalendar"
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
export default {
  data: () => ({
    initialized: false,
    currentSpace: null,
    currentCalendar: null,
    loading: false,
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
    agendaBaseLink: null,
    conferenceProviders: null,
    selectedProviderType: null,
    remoteEventsLoaded: false,
    calendarType: 'month',
    periodTitle: '',
  }),
  computed: {
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
    connectedConnector() {
      return this.connectors && this.connectors.find(connector => connector.connected);
    },
    signedInConnector() {
      return this.connectedConnector && this.connectedConnector.isSignedIn;
    },
    connectorStatus() {
      if (this.connectedConnector) {
        if (this.connectedConnector.isSignedIn) {
          return 1;
        } else {
          return 2;
        }
      } else {
        return 0;
      }
    },

    connectedConnectorAvatar() {
      return this.connectedConnector && this.connectedConnector.avatar || '';
    },
    showDefaultRemoteEvents() {
      return this.settings && this.settings.showRemoteEventsForTimeLine;
    },
    limit() {
      return !this.$root.isTimelineView ? null : this.$root.timelineSettings?.itemsNumber ? this.$root.timelineSettings.itemsNumber : 10;
    },
  },
  watch: {
    limit() {
      this.retrieveEvents();
    },
    initialized() {
      if (this.initialized) {
        this.$root.$emit('agenda-application-loaded');
      }
    },
    period() {
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
    signedInConnector() {
      if (this.signedInConnector && this.settings.showRemoteEventsForTimeLine) {
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
    retrieveEvents() {
      if (this.$root.timelineSettings.agendaSource === 'selectedSpaces') {
        this.ownerIds = this.$root.timelineSettings.selectedSpaces.map(space => space.identityId).filter(id => !!id);
        return this.retrieveEventsFromStore();
      } else if (this.$root.timelineSettings.agendaSource === 'allUsersSpaces'){
        this.ownerIds = [];
        return this.retrieveEventsFromStore();
      } else if (!this.initialized && eXo.env.portal.spaceId) {
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
            this.currentCalendar = data && data.calendars && data.calendars.length && data.calendars[0] || null;
          })
          .finally(() => {
            this.initialized = true;
            this.loading = false;
            this.retrieveEventsFromStore();
          });
      } else {
        if (!eXo.env.portal.spaceId) {
          this.agendaBaseLink = `${eXo.env.portal.context}/${eXo.env.portal.portalName}/agenda`;
        }
        return this.retrieveEventsFromStore();
      }
    },
    retrieveEventsFromStore() {
      this.loading = true;
      const userIdentityId = this.$root.timelineSettings.agendaFilter === 'acceptedEvents' && eXo.env.portal.userIdentityId || null;
      const responseTypes = this.$root.timelineSettings.agendaFilter === 'acceptedEvents' ? ['ACCEPTED'] : ['ACCEPTED', 'NEEDS_ACTION', 'TENTATIVE'];
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
          this.loading = false;
        });
    },
    retrieveRemoteEvents() {
      if (this.connectorStatus === 1) {
        const startDateRFC3359 = this.$agendaUtils.toRFC3339(this.period.start, false, true);
        let endDate = this.period.end ;
        if (!endDate){
          const date = new Date(this.period.start);        
          endDate =  date.setFullYear(date.getFullYear() + 1); 
        }
        const endDateRFC3359 = this.$agendaUtils.toRFC3339(endDate, false, true);
        this.loading = true;
        this.connectedConnector.getEvents(startDateRFC3359, endDateRFC3359)
          .then(events => {
            if (events) {
              events.forEach(event => {
                event.startDate = event.start && this.$agendaUtils.toDate(event.start) || null;
                event.endDate = event.end && this.$agendaUtils.toDate(event.end) || null;
              });
            }
            const remoteEvents = events;
            this.remoteEvents = remoteEvents && remoteEvents.slice() || [];
            this.loading = false;
            this.remoteEventsLoaded = true;
          }).catch(error => {
            this.remoteEvents = [];
            this.loading = false;
            console.error('Error retrieving remote events', error);
          });
      } else {
        this.remoteEvents = [];
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
