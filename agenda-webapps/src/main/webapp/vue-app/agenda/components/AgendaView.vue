<template>
  <v-row class="fill-height">
    <v-col>
      <v-sheet height="64">
        <agenda-toolbar :title="calendar" />
      </v-sheet>
      <v-sheet height="600">
        <agenda-schedule :events="events" />
      </v-sheet>
    </v-col>
  </v-row>
</template>

<script>
export default {
  props: {
    events: {
      type: Array,
      default: () => null
    },
    calendar: {
      type: Object,
      default: () => null
    }
  },
};
</script>
