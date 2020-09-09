<template>
  <v-app class="agenda-application border-box-sizing transparent" flat>
    <v-main class="pa-5">
      <agenda-header
        :calendar-type="calendarType"
        class="mb-5" />
      <agenda-body
        :events="events"
        :period-title="periodTitle"
        :calendar-type="calendarType"
        :weekdays="weekdays" />
      <agenda-event-dialog ref="eventFormDialog" :weekdays="weekdays" />
      <agenda-event-preview-dialog />
      <agenda-calendar-owners-filter-drawer
        :owner-ids="ownerIds"
        @changed="changeDisplayedOwnerIds" />
    </v-main>
  </v-app>
</template>
<script>
export default {
  props: {
    calendarType: {
      type: String,
      default: () => 'week',
    },
  },
  data: () => ({
    loading: false,
    searchTerm: null,
    periodTitle: '',
    period: {
      start: null,
      end: null,
    },
    events: [],
    weekdays: [1, 2, 3, 4, 5, 6, 0],
  }),
  watch: {
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
    this.$root.$on('refresh', this.retrieveEvents);
  },
  mounted() {
    document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
  },
  methods: {
    retrieveEvents() {
      this.loading = true;
      this.$eventService.getEvents(this.searchTerm, this.ownerIds, this.period.start, this.period.end)
        .then(data => {
          const events = data && data.events || [];
          events.forEach(event => {
            event.name = event.summary;
            event.startDate = event.start && new Date(event.start) || null;
            event.endDate = event.end && new Date(event.end) || null;
          });
          this.events = events;
        }).catch(error =>{
          console.error('Error retrieving events', error);
        }).finally(() => this.loading = false);
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
