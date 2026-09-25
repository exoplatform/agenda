<template>
  <application-toolbar
    class="agenda-header mb-0"
    cols-auto="true"
    hide-cone-button="true"
    ref="applicationToolbar">
    <template #left>
      <div class="d-flex">
        <v-btn
          v-if="displayLeftPanelToggle"
          :title="$t('agenda.leftPanel.toggle')"
          :aria-label="$t('agenda.leftPanel.toggle')"
          class="my-auto me-2"
          icon
          width="36"
          height="36"
          @click="toggleLeftPanel">
          <v-icon size="20" class="text-light-color">
            fas fa-calendar-alt
          </v-icon>
        </v-btn>
        <agenda-create-event-button
          :current-space="currentSpace"
          :can-create-event="canCreateEvent" />
        <date-picker
          v-if="$root.isMobile || $root.isTablet"
          v-model="periodStart"
          :attach="false"
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
        :offset-y="offsetY"
        width="36"
        height="36" />
      <extension-registry-components
        :params="params"
        name="AgendaApp"
        type="agenda-app-toolbar"
        parent-element="div"
        element="div"
        class="my-auto" />  
      <!--
        The personal agenda has no show/hide-remote-events toggle: connecting
        an account is the opt-in there and the left panel's per-calendar
        checkboxes decide what is shown. Where there is no left panel (mobile,
        space agenda) the toolbar keeps the connect entry, and a manage entry
        once an account is connected, so the connection itself stays
        reachable.
      -->
      <agenda-connect-to-remote-button
        :connectors="connectors"
        :settings="settings"
        height="36"
        width="36"
        size="20"
        :show-connect-action="!leftPanelCarriesConnect"
        :show-toggle-action="false"
        :show-manage-action="!leftPanelCarriesConnect" />
      <!--
        A space's agenda shows the space's calendars, and the user's own
        accounts only when they ask for them (EXO-90215): a toggle of its own
        beside the manage entry, since one button renders one action.
      -->
      <agenda-connect-to-remote-button
        v-if="inSpace"
        :connectors="connectors"
        :settings="settings"
        :show-default-remote-events="showSpaceRemoteEvents"
        height="36"
        width="36"
        size="20"
        :show-connect-action="false"
        :show-manage-action="false" />
      <agenda-switch-view :calendar-type="calendarType" v-if="!$root.isMobile" />
      <agenda-calendar-filter-button />
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
      // false means 'no calendar selected'
      type: [Array, Boolean],
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
    period: {
      type: Object,
      default: null,
    },
    connectors: {
      type: Array,
      default: () => null,
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
        if (this.$root.isMobile) {
          this.period.start = this.periodStart;
          this.$root.$emit('agenda-refresh');
        } else {
          this.$root.$emit('agenda-display-calendar-atDate', this.$agendaUtils.toRFC3339(newVal, true));
        }
      }
    },
  },
  created() {
    this.periodStart = this.period && this.period.start || new Date();
  },
  methods: {
    /**
     * Notifies the Agenda application that the user asked to collapse or
     * expand the left panel.
     * @returns {void}
     */
    toggleLeftPanel() {
      this.$root.$emit('agenda-left-panel-toggle');
    },
  },
  computed: {
    /**
     * Whether the left panel is carrying the remote-calendar controls, in
     * which case the toolbar shows none of them: connecting and managing are
     * offered beside the calendars they fill. The panel exists only on a
     * desktop personal agenda, so on mobile and inside a space agenda the
     * toolbar remains the only way to reach the connection.
     *
     * @returns {Boolean} true when the left panel carries these controls
     */
    leftPanelCarriesConnect() {
      return !this.$root.isMobile && !eXo.env.portal.spaceId;
    },
    /**
     * The space the agenda shows, as the application resolved it: a
     * standalone agenda opened from a space page is the personal one.
     *
     * @returns {Boolean} true inside a space's agenda
     */
    inSpace() {
      return !!this.currentSpace;
    },
    /**
     * @returns {Boolean} whether the space's agenda shows the user's own
     *          accounts' events
     */
    showSpaceRemoteEvents() {
      return !!this.settings?.showRemoteEventsForSpaceAgenda;
    },

    /**
     * Whether the left panel toggle button is displayed: desktop only, and
     * only where the left panel itself is available (personal agenda, not
     * inside a space).
     *
     * @returns {boolean} true when the toggle button must be displayed
     */
    displayLeftPanelToggle() {
      return !this.$root.isMobile && !eXo.env.portal.spaceId;
    },
    /**
     * Whether the current user can create an event in the displayed calendar.
     *
     * @returns {boolean} true when event creation is allowed
     */
    canCreateEvent() {
      return !this.currentCalendar || !this.currentCalendar.acl || this.currentCalendar.acl.canCreate;
    },
    params() {
      return {
        space: this.currentSpace,
        calendar: this.currentCalendar,
        ownerIds: this.ownerIds,
      };
    }
  },
};
</script>