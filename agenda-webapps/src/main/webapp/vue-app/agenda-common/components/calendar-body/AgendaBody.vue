<template>
  <v-card class="application-body pa-4" flat>
    <v-flex class="d-flex flex-column agenda-body">
      <agenda-calendar
        :events="events"
        :current-calendar="currentCalendar"
        :calendar-type="calendarType"
        :weekdays="weekdays"
        :full-weekdays="fullWeekdays"
        :working-time="workingTime"
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
    currentCalendar: {
      type: Object,
      default: () => null
    },
    calendarType: {
      type: String,
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
