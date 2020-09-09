<template>
  <v-toolbar
    color="white"
    flat
    dense>
    <agenda-create-event-button class="mr-2" />
    <agenda-sync-event-button />
    <v-spacer />
    <agenda-switch-view :calendar-type="calendarType" />
    <v-spacer />
    <agenda-search-event />
    <agenda-pulldown-event class="ml-4 pr-2" />
    <v-btn icon @click="$root.$emit('agenda-calendar-owners-drawer-open')">
      <v-icon>mdi-format-list-bulleted</v-icon>
    </v-btn>
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