<template>
  <v-app class="agenda-application border-box-sizing transparent" flat>
    <v-main class="pa-5">
      <agenda-header
        :calendar-type="calendarType"
        class="mb-5"
      />
      <agenda-body
        :events="events"
        :period-title="periodTitle"
        :calendar-type="calendarType"
      />
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
    period: {
      start: null,
      end: null,
    },
    events: [],
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
      // FIXME
      this.periodTitle = 'TODO';
    });
    this.$root.$on('agenda-change-period-type', calendarType => this.calendarType = calendarType);
    this.$root.$on('agenda-search', searchTerm => this.searchTerm = searchTerm);
    this.$root.$on('agenda-open-event-form', event => this.$refs.eventFormDialog.open(event));
  },
  mounted() {
    document.dispatchEvent(new CustomEvent('hideTopBarLoading'));
  },
  methods: {
    retrieveEvents() {
      this.loading = true;
      this.$eventService.getEvents(this.searchTerm, this.ownerId, this.period.start, this.period.end)
        .then(events => {
          this.events = events || [];
          this.events.forEach(event => {
            event.name = event.summary;
          });
        }).catch(error =>{
          console.error('Error retrieving events', error);
        }).finally(() => this.loading = false);
    }
  },
};
</script>
