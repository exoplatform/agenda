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
      <agenda-event-dialog ref="eventFormDialog" />
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
    ownerId: eXo.env.portal.userIdentityId,
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
      this.periodTitle = this.generateCalendarTitle();
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
      this.periodTitle = period.title;
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
      this.$eventService.getEvents(this.searchTerm, this.ownerId, this.period.start, this.period.end)
        .then(data => {
          this.events = data && data.events || [];
          this.events.forEach(event => {
            event.name = event.summary;
          });
        }).catch(error =>{
          console.error('Error retrieving events', error);
        }).finally(() => this.loading = false);
    },
    generateCalendarTitle() {
      let generatedPeriodTitle = this.periodTitle;
      if(this.calendarType === 'week') {
        const weekNumber = this.$agendaUtils.getWeekNumber(new Date(this.period.start));
        generatedPeriodTitle = `${this.periodTitle} - ${this.$t('agenda.header.toolbar.title.week')} ${weekNumber}`;
      } else if (this.calendarType === 'day') {
        const currentDay = new Date(this.period.start).getDate();
        generatedPeriodTitle = `${this.periodTitle} - ${currentDay}`;
      }
      return generatedPeriodTitle;
    }
  },
};
</script>
