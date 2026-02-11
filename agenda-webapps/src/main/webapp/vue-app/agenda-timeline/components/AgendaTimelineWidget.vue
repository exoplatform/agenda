<template>
  <v-app class="agenda-application" flat>
    <v-card class="d-flex flex-column application-body position-static pa-5 border-box-sizing" flat>
      <agenda-timeline-header
        :current-space="currentSpace"
        :current-calendar="currentCalendar"
        :agenda-base-link="agendaBaseLink"
        :connectors="enabledConnectors"
        :settings="settings"
        :show-default-remote-events="showDefaultRemoteEvents" />
      <agenda-timeline
        :events="displayedEvent"
        :period-start-date="periodStart"
        :agenda-base-link="agendaBaseLink"
        :loading="loading || !initialized"
        :limit="limit"
        :connected-connector-avatar="connectedConnectorAvatar" />
    </v-card>
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
  </v-app>
</template>
<script>
export default {
  props: {
    eventType: {
      type: String,
      default: () => 'myEvents',
    }
  },
  data: () => ({
    initialized: false,
    currentSpace: null,
    currentCalendar: null,
    loading: false,
    ownerIds: [],
    connectors: [],
    periodStart: new Date(),
    limit: 10,
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
    displayedEvent() {
      if (this.showDefaultRemoteEvents){
        return  this.events.concat(this.remoteEvents).sort((event1, event2) => {
          const eventStart1 = this.$agendaUtils.toDate(event1.start || event1.startDate).getTime();
          const eventStart2 = this.$agendaUtils.toDate(event2.start || event2.startDate).getTime();
          return eventStart1 - eventStart2;
        });
      }
      return  this.events;
    },
  },
  watch: {
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
    }
  },
  created() {
    if (eXo.env.portal.spaceId) {
      this.limit = 5;
    }
    this.retrieveEvents().finally(() => document.dispatchEvent(new CustomEvent('hideTopBarLoading')));
    this.$root.$on('agenda-settings-refresh', this.initSettings);
    this.$root.$on('agenda-refresh', this.retrieveEvents);
    this.$root.$on('agenda-event-saved', this.retrieveEvents);
    this.$root.$on('agenda-event-deleted', this.retrieveEvents);
    this.$root.$on('agenda-event-change-owner', this.refreshProviders);
    this.$root.$on('agenda-show-remote-change', this.showRemoteEvents);
    this.spaceId = eXo.env.portal.spaceId;
    document.addEventListener('drawerOpened', () => this.$el.closest('#stickyBlockDesktop').style.position = 'static');
    document.addEventListener('drawerClosed', () => this.$el.closest('#stickyBlockDesktop').style.position = 'sticky');
    // Asynchronously load settings to use it in dialogs,
    // not needed for main screen display
    this.initSettings();
  },
  beforeDestroy() {
    document.removeEventListener('drawerOpened', () => this.$el.closest('#stickyBlockDesktop').style.position = 'static');
    document.removeEventListener('drawerClosed', () => this.$el.closest('#stickyBlockDesktop').style.position = 'sticky');
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
      if (!this.initialized && eXo.env.portal.spaceId) {
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
      const userIdentityId = this.eventType === 'myEvents' && eXo.env.portal.userIdentityId || null;
      const responseTypes = ['ACCEPTED','TENTATIVE'];
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
  },
};
</script>
