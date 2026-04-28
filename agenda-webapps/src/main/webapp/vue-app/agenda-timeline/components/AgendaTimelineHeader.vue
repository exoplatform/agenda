<template>
  <div class="agenda-timeline-header d-flex align-center justify-space-between">
    <div class="d-flex align-center">
      <a :href="agendaBaseLink" class="widget-text-header my-auto me-auto">
        {{ $t('agenda') }}
      </a>
      <agenda-period-selector
        v-if="!$root.isMobile"
        :period-title="periodTitle" />  
    </div>
    <v-spacer />
    <v-tooltip
      v-if="!$root.isMobile && $root.hover && $root.canEdit"
      max-width="300"
      bottom>
      <template #activator="{ on, attrs }">
        <v-btn
          id="agendaTimeLineSettingsButton"
          small
          icon
          v-bind="attrs"
          v-on="on"
          @click="$root.$emit('open-agenda-timeline-settings')">
          <v-icon size="20">fa-cog</v-icon>
        </v-btn>
      </template>
      <span>
        {{ $t('agenda.settings.button.tooltip') }}
      </span>
    </v-tooltip>
    <agenda-pending-invitation-badge
      :current-space="currentSpace"
      :offset-y="18"
      :offset-x="12" />
    <agenda-pending-invitation-badge
        :current-space="currentSpace"
        :offset-y="18"
        :offset-x="12" />
    <agenda-connect-to-remote-button
      :connectors="connectors"
      :settings="settings"
      :show-default-remote-events="showDefaultRemoteEvents" /> 
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
    periodTitle: {
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
    showDefaultRemoteEvents: {
      type: Boolean,
      default: false,
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