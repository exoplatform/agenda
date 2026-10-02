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
    /**
     * Opens the creation form the button's context calls for.
     *
     * The compact layout ($root.isMobile) is also what the application
     * renders when its container is narrow on a desktop viewport: the App
     * Center drawer, a narrow page column. There the application is a
     * widget, and the quick drawer is what the timeline widget's own "+"
     * opens, with the full form one "More details" away. On a phone the
     * mobile form opens directly: the quick drawer there is the empty
     * timeline's route, not this button's. On a desktop page the full form
     * opens directly, the desktop calendar's own route.
     *
     * @returns {void}
     */
    openNewEventForm() {
      const event = {
        summary: '',
        allDay: false,
        calendar: {
          owner: {},
        },
        reminders: [],
        attachments: [],
        attendees: [],
      };
      if (this.$root.isMobile && !this.$vuetify.breakpoint.smAndDown) {
        event.startDate = new Date();
        event.endDate = new Date();
        this.$root.$emit('agenda-event-quick-form', event);
      } else {
        this.$root.$emit('agenda-event-form', event);
      }
    },
    setToday() {
      this.$root.$emit('agenda-display-calendar-atDate');
    },
  },
  
};
</script>
