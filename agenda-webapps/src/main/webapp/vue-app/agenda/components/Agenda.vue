<template>
  <v-app class="agenda-application border-box-sizing transparent" flat>
    <v-main v-if="settingsLoaded" class="pa-5">
      <agenda-header
        :calendar-type="calendarType"
        :event-type="eventType"
        :current-space="currentSpace"
        :owner-ids="ownerIds"
        class="mb-5" />
      <agenda-body
        :events="events"
        :period-title="periodTitle"
        :calendar-type="calendarType"
        :weekdays="weekdays"
        :working-time="workingTime" />
      <agenda-event-dialog
        ref="eventFormDialog"
        :current-space="currentSpace"
        :weekdays="weekdays"
        :working-time="workingTime" />
      <agenda-event-preview-dialog />
      <agenda-filter-calendar-drawer
        :owner-ids="ownerIds"
        @changed="changeDisplayedOwnerIds" />
      <agenda-user-setting-drawer :settings="settings" />
      <agenda-event-quick-form-drawer :current-space="currentSpace" />
      <agenda-event-save />
    </v-main>
  </v-app>
</template>
<script>
export default {
  data: () => ({
    initialized: false,
    currentSpace: null,
    filterCanceledEvents: true,
    loading: false,
    ownerIds: [],
    searchTerm: null,
    eventType: 'myEvents',
    periodTitle: '',
    calendarType: 'week',
    period: {
      start: null,
      end: null,
    },
    events: [],
    settings: {
      agendaDefaultView: 'week',
      agendaWeekStartOn: 'MO',
      showWorkingTime: false,
      workingTimeStart: '00:00',
      workingTimeEnd: '23:59',
    },
    settingsLoaded: false,
  }),
  computed: {
    weekdays () {
      return this.settings && this.$agendaUtils.getWeekSequenceFromDay(this.settings.agendaWeekStartOn);
    },
    workingTime () {
      return this.settings && {
        showWorkingTime: this.settings.showWorkingTime,
        workingTimeStart: this.settings.workingTimeStart,
        workingTimeEnd: this.settings.workingTimeEnd
      };
    }
  },
  watch: {
    eventType() {
      this.retrieveEvents();
    },
    period() {
      this.retrieveEvents();
    },
    searchTerm() {
      this.retrieveEvents();
    },
    loading() {
      if (this.loading) {
        document.dispatchEvent(new CustomEvent('displayTopBarLoading'));
      } else {
        document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
      }
    },
  },
  created() {
    this.$root.$on('agenda-change-period', period => {
      this.period = period;
      this.periodTitle = this.generateCalendarTitle(period);
    });
    this.$root.$on('agenda-change-period-type', calendarType => this.calendarType = calendarType);
    this.$root.$on('agenda-search', searchTerm => this.searchTerm = searchTerm);
    this.$root.$on('agenda-event-saved', this.retrieveEvents);
    this.$root.$on('refresh', this.retrieveEvents);
    this.$root.$on('agenda-event-type-changed', eventType => this.eventType = eventType);
    this.$root.$on('agenda-event-deleted', this.retrieveEvents);
    this.spaceId = eXo.env.portal.spaceId;
    this.$calendarService.getAgendaSettings()
      .then(settings => {
        if (settings && settings.value) {
          this.settings = JSON.parse(settings.value);
          this.calendarType = this.settings && this.settings.agendaDefaultView;
        }
      })
      .finally(() => {
        this.settingsLoaded = true;
        document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
      });
  },
  methods: {
    retrieveEvents() {
      if (!this.initialized && eXo.env.portal.spaceId) {
        const spaceId = eXo.env.portal.spaceId;
        this.$spaceService.getSpaceById(spaceId, 'identity')
          .then((space) => {
            this.currentSpace = space;
            if (space && space.identity && space.identity.id) {
              this.ownerIds = [space.identity.id];
            }
            this.retrieveEventsFromStore();
          });
      } else {
        this.retrieveEventsFromStore();
      }
    },
    retrieveEventsFromStore() {
      this.loading = true;
      const userIdentityId = this.eventType === 'myEvents' && eXo.env.portal.userIdentityId || null;
      this.$eventService.getEvents(this.searchTerm, this.ownerIds, userIdentityId, this.period.start, this.period.end)
        .then(data => {
          let events = data && data.events || [];
          if (this.filterCanceledEvents) {
            events = events.filter(event => !event.status || event.status !== 'CANCELED');
          }
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
    generateCalendarTitle(period) {
      return this.$agendaUtils.generateCalendarTitle(this.calendarType, new Date(period.start), period.title, this.$t('agenda.header.toolbar.title.week'));
    },
    changeDisplayedOwnerIds(selectedOwnerIds) {
      this.ownerIds = selectedOwnerIds;
      this.retrieveEvents();
    },
  },
};
</script>
