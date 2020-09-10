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
    <select
      v-model="eventType"
      class="width-auto my-auto subtitle-1 ignore-vuetify-classes d-none d-sm-inline"
      @change="$root.$emit('agenda-event-type-changed', eventType)">
      <option value="myEvents">{{ $t('agenda.myEvent') }}</option>
      <option value="allEvents">{{ $t('agenda.allEvent') }}</option>
    </select>
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
    eventType: {
      type: String,
      default: null
    },
  },
};
</script>