<template>
  <div class="agenda-timeline-header d-flex align-center justify-space-between">
    <div class="d-flex align-center">
      <a :href="agendaBaseLink" class="widget-text-header my-auto me-auto">
        {{ $t('agenda') }}
      </a>
      <agenda-pending-invitation-badge
        :current-space="currentSpace"
        :offset-y="18"
        :offset-x="12" />
    </div>
    <v-spacer />
    <agenda-connect-to-remote-button
      :connectors="connectors"
      :settings="settings" /> 
    <div
      v-if="displayButton"
      :title="addEventButtonTooltip"
      class="d-flex align-center">
      <v-btn
        :disabled="!canCreateEvent"
        :title="$t('agenda.button.addEvent')"
        icon
        class="ms-auto"
        small
        @click="openEventForm">
        <v-icon size="18" class="icon-default-color icon-default-size">
          fas fa-plus
        </v-icon>
      </v-btn>
    </div>
  </div>
</template>
<script>
export default {
  props: {
    currentSpace: {
      type: Object,
      default: null
    },
    currentCalendar: {
      type: Object,
      default: null
    },
    agendaBaseLink: {
      type: String,
      default: null
    },
    connectors: {
      type: Array,
      default: () => null,
    },
    settings: {
      type: Object,
      default: () => null
    },
  },
  data: () => ({
    initialized: false,
  }),
  computed: {
    displayButton() {
      return (!this.$root.isMobile || this.canCreateEvent) && (this.initialized || !eXo.env.portal.spaceId);
    },
    canCreateEvent() {
      return !this.currentCalendar || !this.currentCalendar.acl || this.currentCalendar.acl.canCreate;
    },
    addEventButtonTooltip() {
      if (!this.canCreateEvent) {
        return this.$t('agenda.onlySpaceRedactorCanCreateEvent');
      }
      return '';
    },
  },
  created() {
    this.$root.$on('agenda-application-loaded', () => this.initialized = true);
  },
  methods: {
    openEventForm() {
      this.$root.$emit('agenda-event-quick-form', {
        summary: '',
        startDate: new Date(),
        endDate: new Date(),
        allDay: false,
        calendar: {
          owner: {},
        },
        reminders: [],
        attachments: [],
        attendees: [],
      });
    },
    openPersonalCalendarDrawer() {
      this.$root.$emit('agenda-connectors-drawer-open');
    },
  },
};
</script>