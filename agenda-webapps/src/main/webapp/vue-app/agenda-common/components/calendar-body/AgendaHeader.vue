<template>
  <application-toolbar
    class="mb-0"
    cols-auto="true"
    ref="applicationToolbar">
    <template #left>
      <div class="d-flex">
        <agenda-create-event-button
          :current-space="currentSpace"
          :can-create-event="canCreateEvent" />
      </div>
    </template>
    <template #center>
      <div class="d-flex">
        <v-btn
          max-width="36"
          max-height="36"
          icon
          @click="prevDate">
          <v-icon size="20">
            fa-chevron-left
          </v-icon>
        </v-btn>
        <v-btn
          max-width="36"
          max-height="36"
          icon
          @click="nextDate">
          <v-icon size="20">
            fa-chevron-right
          </v-icon>
        </v-btn>
        <div class="period-title text-uppercase my-auto ms-2">
          {{ periodTitle }}
        </div>
      </div>
    </template>
    <template #right>
      <agenda-switch-view :calendar-type="calendarType" />
      <agenda-calendar-filter-button
        :current-space="currentSpace"
        :owner-ids="ownerIds"
        class="ms-2" />
      <extension-registry-components
        :params="params"
        name="AgendaApp"
        type="agenda-app-toolbar"
        parent-element="div"
        element="div"
        class="my-auto" />
    </template>
  </application-toolbar>
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
    periodTitle: {
      type: String,
      default: null
    },
  },
  computed: {
    canCreateEvent() {
      return !this.currentCalendar || !this.currentCalendar.acl || this.currentCalendar.acl.canCreate;
    },
    params() {
      return {
        space: this.currentSpace,
        calendar: this.currentCalendar,
        ownerIds: this.ownerIds,
      };
    },
  },
  methods: {
    nextDate() {
      this.$root.$emit('agenda-display-calendar-next');
    },
    prevDate() {
      this.$root.$emit('agenda-display-calendar-previous');
    }
  },
};
</script>