<template>
  <application-toolbar
    class="agenda-header mb-0"
    cols-auto="true"
    hide-cone-button="true"
    ref="applicationToolbar">
    <template #left>
      <div class="d-flex">
        <agenda-create-event-button
          :current-space="currentSpace"
          :can-create-event="canCreateEvent" />
        <date-picker
          v-if="$root.isMobile || $root.isTablet"
          v-model="periodStart"
          class="agenda-header-date-picker z-index-two" />  
        <agenda-period-selector
          v-else
          :period-title="periodTitle" />  
      </div>
    </template>
    <template #right>
      <agenda-pending-invitation-badge
        :current-space="currentSpace"
        :offset-x="offsetX"
        :offset-y="offsetY" />
      <agenda-switch-view :calendar-type="calendarType" v-if="!$root.isMobile" />
      <agenda-calendar-filter-button />
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
    settings: {
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
    offsetX: {
      type: Number,
      default: () => 18,
    },
    offsetY: {
      type: Number,
      default: () => 22,
    },
  },
  data: () => ({
    periodStart: null,
  }),
  watch: {
    periodStart(newVal, oldVal) {
      if (!oldVal || !newVal) {
        return;
      }
      if (this.$agendaUtils.toRFC3339(oldVal, true) !== this.$agendaUtils.toRFC3339(newVal, true)) {
        this.period.start = this.periodStart;
        this.$root.$emit('agenda-refresh');
      }
    },
  },
  created() {
    this.periodStart = this.period && this.period.start || new Date();
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
};
</script>