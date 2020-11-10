<template>
  <v-app class="agenda-application border-box-sizing" flat>
    <v-main class="white">
      <agenda-timeline-header
        :current-space="currentSpace"
        :agenda-base-link="agendaBaseLink" />
      <agenda-timeline
        :events="events"
        :period-start-date="periodStart"
        :agenda-base-link="agendaBaseLink"
        :loading="loading || !initialized"
        :limit="limit" />
    </v-main>
    <agenda-event-dialog
      ref="eventFormDialog"
      :current-space="currentSpace"
      :weekdays="weekdays"
      :working-time="workingTime" />
    <agenda-event-quick-form-drawer
      :current-space="currentSpace"
      :display-more-details="false" />
    <agenda-event-save />
  </v-app>
</template>
<script>
export default {
  data: () => ({
    initialized: false,
    currentSpace: null,
    loading: false,
    ownerIds: [],
    eventType: 'myEvents',
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
    agendaBaseLink: null,
  }),
  computed: {
    weekdays() {
      return this.settings && this.$agendaUtils.getWeekSequenceFromDay(this.settings.agendaWeekStartOn);
    },
    workingTime() {
      return this.settings && {
        showWorkingTime: this.settings.showWorkingTime,
        workingTimeStart: this.settings.workingTimeStart,
        workingTimeEnd: this.settings.workingTimeEnd
      };
    },
  },
  watch: {
    limit() {
      this.retrieveEvents();
    },
    eventType() {
      this.retrieveEvents();
    },
    period() {
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
    this.retrieveEvents().finally(() => document.dispatchEvent(new CustomEvent('hideTopBarLoading')));
    this.$root.$on('agenda-event-saved', this.retrieveEvents);
    this.$root.$on('agenda-refresh', this.retrieveEvents);
    this.spaceId = eXo.env.portal.spaceId;
  },
  methods: {
    retrieveEvents() {
      if (!this.initialized && eXo.env.portal.spaceId) {
        const spaceId = eXo.env.portal.spaceId;
        return this.$spaceService.getSpaceById(spaceId, 'identity')
          .then((space) => {
            this.currentSpace = space;
            if (space && space.identity && space.identity.id) {
              this.ownerIds = [space.identity.id];
              const spaceGroupUri = this.currentSpace.groupId.replace(/\//g, ':');
              this.agendaBaseLink = `${eXo.env.portal.context}/g/${spaceGroupUri}/${this.currentSpace.prettyName}/Agenda`;
            } else {
              this.agendaBaseLink = `${eXo.env.portal.context}/${eXo.env.portal.portalName}/agenda`;
            }
            return this.retrieveEventsFromStore();
          })
          .finally(() => {
            this.initialized = true;
            this.loading = false;
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
      return this.$eventService.getEvents(this.searchTerm, this.ownerIds, userIdentityId, this.$agendaUtils.toRFC3339(this.period.start, true), this.$agendaUtils.toRFC3339(this.period.end), this.limit, responseTypes)
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
  },
};
</script>
