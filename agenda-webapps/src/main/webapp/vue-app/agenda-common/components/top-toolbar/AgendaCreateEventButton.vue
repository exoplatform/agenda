<template>
  <div class="text-no-wrap">
    <div
      v-if="displayButton"
      :title="addEventButtonTooltip"
      class="d-inline-block">
      <v-btn
        :disabled="!canCreateEvent"
        class="btn btn-primary px-0 me-2"
        min-width="36"
        max-width="36"
        @click="openNewEventForm">
        <v-icon dark size="18">
          fa-plus
        </v-icon>
      </v-btn>
    </div>
    <agenda-pending-invitation-badge
      :current-space="currentSpace"
      :offset-x="offsetX"
      :offset-y="offsetY" />
    <v-btn
      v-if="!$root.isMobile"
      class="btn me-2"
      @click="setToday">
      {{ $t('agenda.toDay') }}
    </v-btn>
  </div>
</template>
<script>
export default {
  props: {
    currentSpace: {
      type: Object,
      default: null
    },
    canCreateEvent: {
      type: Boolean,
      default: false,
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
