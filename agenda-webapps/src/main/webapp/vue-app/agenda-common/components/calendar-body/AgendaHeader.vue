<template>
  <v-toolbar
    color="white card-border-radius"
    height="60"
    flat
    dense>
    <v-row class="pa-5">
      <v-col
        cols="2"
        sm="4"
        class="align-start my-auto px-0">
        <agenda-create-event-button
          :current-space="currentSpace"
          :can-create-event="canCreateEvent"
          class="agenda-toolbar-left me-2" />
      </v-col>
      <v-col
        cols="8"
        sm="4"
        align="center"
        class="d-flex flex-row align-center justify-start flex-nowrap px-0">
        <agenda-switch-view :calendar-type="calendarType" />
      </v-col>
      <v-col
        cols="2"
        sm="4"
        class="d-flex flex-row justify-end my-auto flex-nowrap px-0">
        <select
          v-model="eventType"
          class="width-auto my-auto ms-4 subtitle-1 ignore-vuetify-classes d-none d-sm-inline"
          @change="$root.$emit('agenda-event-type-changed', eventType)">
          <option value="myEvents">{{ $t('agenda.myEvent') }}</option>
          <option value="declinedEvent">{{ $t('agenda.declinedEvent') }}</option>
          <option value="allEvents">{{ $t('agenda.allEvent') }}</option>
        </select>
        <agenda-calendar-filter-button
          :current-space="currentSpace"
          :owner-ids="ownerIds"
          class="ms-2" />
        <v-btn
          icon
          :title="$t('agenda.settings.drawer.title')"
          class="d-none d-sm-inline text-header-title"
          @click="$root.$emit('user-settings-agenda-drawer-open')">
          <v-icon>mdi-cog</v-icon>
        </v-btn>
      </v-col>
    </v-row>
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
    currentCalendar: {
      type: Object,
      default: () => null
    },
    ownerIds: {
      type: Array,
      default: null
    },
  },
  computed: {
    canCreateEvent() {
      return !this.currentCalendar || !this.currentCalendar.acl || this.currentCalendar.acl.canCreate;
    },
  },
};
</script>