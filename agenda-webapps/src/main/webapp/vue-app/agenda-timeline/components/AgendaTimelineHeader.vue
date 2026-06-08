<template>
  <div class="agenda-timeline-header d-flex align-center justify-space-between px-5 pt-5">
    <div class="d-flex col-8">
      <div class="widget-text-header text-truncate">
        {{ headerTitle }}
      </div>
      <agenda-period-selector
        v-if="!$root.isTimelineView"
        :period-title="periodTitle" />  
    </div>
    <div class="d-flex justify-end col-4">
      <agenda-pending-invitation-badge
        v-if="displayPendingEvents"
        :current-space="currentSpace"
        :offset-y="18"
        :offset-x="12" />
      <agenda-connect-to-remote-button
        width="28"
        height="28"
        size="20"
        :connectors="connectors"
        :settings="settings"
        :show-default-remote-events="showDefaultRemoteEvents" /> 
      <div
        v-if="displayButton"
        :title="addEventButtonTooltip"
        class="d-flex align-center">
        <v-btn
          :title="$t('agenda.button.addEvent')"
          icon
          class="ms-auto"
          max-width="28"
          max-height="28"
          @click="openEventForm">
          <v-icon size="20" class="icon-default-color icon-default-size">
            fas fa-plus
          </v-icon>
        </v-btn>
      </div>
      <v-btn
        v-if="displaySeeMore && ($root.isMobile || !$root.hover)"
        ref="moreButton"
        class="flex-shrink-0 flex-grow-0 px-0 ps-3"
        color="primary"
        height="28"
        text
        @click="openSeeMoreLink">
        {{ $t('agenda.timeline.seeMore') }}
      </v-btn>
      <v-btn
        v-if="!$root.isMobile && $root.hover && displaySeeMore"
        id="agendaTimeLinerxtarnalLinkButton"
        :title="$t('agenda.timeline.seeMore.tooltip')"
        color="primary"
        icon
        max-width="28"
        max-height="28"
        @click="openSeeMoreLink">
        <v-icon size="20">fa-external-link-alt</v-icon>
      </v-btn>
      <v-btn
        v-if="!$root.isMobile && $root.hover && $root.canEdit"
        id="agendaTimeLineSettingsButton"
        :title="$t('agenda.settings.button.tooltip')"
        max-width="28"
        max-height="28"
        icon
        @click="$root.$emit('open-agenda-timeline-settings')">
        <v-icon size="20">fa-cog</v-icon>
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
    eventsCount: {
      type: Number,
      default: 0,
    },
    calendars: {
      type: Array,
      default: () => []
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
    canCreateEvent: {
      type: Boolean,
      default: false,
    },
  },
  data: () => ({
    initialized: false,
  }),
  computed: {
    headerTitle() {
      return this.$root.timelineSettings.customHeader && this.$root.headerTitle!=='null' ? this.$root.headerTitle : this.$t('agenda');
    },
    displayButton() {
      return this.eventsCount > 0 && (this.initialized || !eXo.env.portal.spaceId) && !this.$root.isMobile && this.canCreateEvent &&  this.$root.timelineSettings.displayAddEvent !== false;
    },
    displaySeeMore() {
      return this.$root.timelineSettings.displaySeeMore !== false ;
    },
    displayPendingEvents() {
      return this.$root.timelineSettings.displayPending;
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
    openSeeMoreLink () {
      let url = this.$root.timelineSettings.seeMoreUrl;
      if (!url) {
        if (eXo.env.portal.spaceId) {    
          url = `${eXo.env.portal.context}/s/${eXo.env.portal.spaceId}/agenda`;
        } else {
          url = `${eXo.env.portal.context}/${eXo.env.portal.portalName}/agenda`;
        }
      }
      if (url.startsWith(eXo.env.portal.context)) {
        window.open(url, '_self');
      } else {
        window.open(url, '_blank');
      }
    }, 
  },
};
</script>