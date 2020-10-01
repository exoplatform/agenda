<template>
  <v-toolbar
    color="white"
    flat
    dense>
    <agenda-create-event-button class="mr-2" />
    <v-spacer />
    <agenda-switch-view :calendar-type="calendarType" />
    <v-spacer />
    <select
      v-model="eventType"
      class="width-auto my-auto ml-4 subtitle-1 ignore-vuetify-classes d-none d-sm-inline"
      @change="$root.$emit('agenda-event-type-changed', eventType)">
      <option value="myEvents">{{ $t('agenda.myEvent') }}</option>
      <option value="allEvents">{{ $t('agenda.allEvent') }}</option>
    </select>
    <agenda-calendar-filter-button
      :current-space="currentSpace"
      :owner-ids="ownerIds"
      class="ml-2" />
    <v-btn
      icon
      :title="$t('agenda.settings.drawer.title')"
      @click="$root.$emit('user-settings-agenda-drawer-open')">
      <v-icon>mdi-settings</v-icon>
    </v-btn>
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
    currentSpace: {
      type: Object,
      default: null
    },
    ownerIds: {
      type: Array,
      default: null
    },
  },
};
</script>