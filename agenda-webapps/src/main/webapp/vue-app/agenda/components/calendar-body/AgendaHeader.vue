<template>
  <v-toolbar
    color="white"
    flat
    dense
  >
    <agenda-create-event-button class="mr-2" />
    <agenda-sync-event-button />
    <v-spacer />
    <agenda-switch-view :calendar-type="calendarType" />
    <v-spacer />
    <agenda-search-event />
    <agenda-pulldown-event class="ml-4 pr-2" />
    <agenda-filter-button />
    <agenda-settings-button />
  </v-toolbar>
</template>


<script>
export default {
  props: {
    calendarType: {
      type: String,
      default: null
    },
  },
};
</script>