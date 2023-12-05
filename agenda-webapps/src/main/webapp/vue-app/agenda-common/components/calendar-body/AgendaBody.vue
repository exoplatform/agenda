<template>
  <v-card class="card-border-radius pa-5" flat>
    <v-flex class="d-flex flex-column agenda-body">
      <agenda-toolbar :period-title="periodTitle" class="mb-5" />
      <agenda-calendar
        :events="events"
        :current-calendar="currentCalendar"
        :calendar-type="calendarType"
        :weekdays="weekdays"
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
