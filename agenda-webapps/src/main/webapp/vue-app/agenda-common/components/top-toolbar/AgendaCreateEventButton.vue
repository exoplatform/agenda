<template>
  <div class="text-no-wrap">
    <div
      v-if="displayButton"
      :title="addEventButtonTooltip"
      class="d-inline-block">
      <v-btn
        :disabled="!canCreateEvent"
        class="me-2"
        icon
        width="36"
        height="36"
        @click="openNewEventForm">
        <v-icon size="20" class="text-light-color">
          fa-plus
        </v-icon>
      </v-btn>
    </div>
    <v-btn
      v-if="!$root.isMobile"
      class="btn me-3"
      max-height="34"
      @click="setToday">
      {{ $t('agenda.toDay') }}
    </v-btn>
  </div>
</template>
<script>
export default {
  props: {
    canCreateEvent: {
      type: Boolean,
      default: false,
    }
  },
  data: () => ({
    initialized: false,
  }),
  computed: {
    displayButton() {
      return (!this.$root.isMobile || this.canCreateEvent) && (this.initialized || !eXo.env.portal.spaceId);
    },
    addEventButtonTooltip() {
      if (!this.canCreateEvent) {
        return this.$t('agenda.onlySpaceRedactorCanCreateEvent');
      }
      return this.$t('agenda.button.addEvent');
    },
  },
  created() {
    this.$root.$on('agenda-application-loaded', () => this.initialized = true);
  },
  methods: {
    openNewEventForm(){
      this.$root.$emit('agenda-event-form', {
        summary: '',
        allDay: false,
        calendar: {
          owner: {},
        },
        reminders: [],
        attachments: [],
        attendees: [],
      });
    },
    setToday() {
      this.$root.$emit('agenda-display-calendar-atDate');
    },
  },
  
};
</script>
