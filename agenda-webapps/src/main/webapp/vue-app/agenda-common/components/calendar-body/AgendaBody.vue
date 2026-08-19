<template>
  <v-card class="application-body pa-5" flat>
    <v-flex class="d-flex flex-column agenda-body">
      <agenda-calendar
        :events="events"
        :calendars="calendars"
        :calendar-type="calendarType"
        :weekdays="weekdays"
        :full-weekdays="fullWeekdays"
        :working-time="workingTime"
        :connected-connector="connectedConnector" 
        class="flex-grow-1" />
    </v-flex>
  </v-card>
</template>

<script>
export default {
  props: {
    periodTitle: {
      type: String,
      default: () => null
    },
    calendars: {
      type: Array,
      default: () => []
    },
    calendarType: {
      type: String,
      default: null
    },
    connectedConnector: {
      type: Object,
      default: null
    },
    weekdays: {
      type: Array,
      default: () => null
    },
    fullWeekdays: {
      type: Array,
      default: () => null
    },
    workingTime: {
      type: Object,
      default: () => null
    },
    events: {
      type: Array,
      default: () => null
    },
  },
};
</script>
