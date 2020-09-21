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
    <v-btn
      icon
      class="ml-2"
      @click="$root.$emit('agenda-calendar-owners-drawer-open')">
      <i class="uiIcon uiIcon24x24 settingsIcon text-color"></i>
    </v-btn>
    <v-btn
      icon
      @click="$root.$emit('agenda-settings-drawer-open')">
      <v-icon class="text-color">mdi-settings</v-icon>
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
  },
};
</script>