<template>
  <v-flex>
    <agenda-toolbar :period-title="periodTitle" />
    <agenda-calendar :events="events" :calendar-type="calendarType" />
  </v-flex>
</template>

<script>
export default {
  props: {
    periodTitle: {
      type: String,
      default: () => null
    },
    calendarType: {
      type: String,
      default: null
    },
    events: {
      type: Array,
      default: () => null
    },
  },
};
</script>
