<template>
  <div v-if="showButton" class="d-flex align-center">
    <v-btn
      v-if="!connectedConnector && showConnectAction && !caldavManaged"
      :title="$t('agenda.connectYourPersonalAgenda')"
      icon
      :max-width="width"
      :max-height="height" 
      @click="openPersonalCalendarDrawer">
      <v-icon :size="size" class="text-light-color">
        fas fa-plug
      </v-icon>
    </v-btn>
    <v-btn
      v-else-if="connectedConnector && showManageAction && !caldavManaged"
      :title="$t('agenda.manageYourPersonalAgenda')"
      icon
      :max-width="width"
      :max-height="height"
      @click="openPersonalCalendarDrawer">
      <v-icon :size="size" class="text-light-color">
        fas fa-plug
      </v-icon>
    </v-btn>
    <v-btn
      v-else-if="connectedConnector && showToggleAction"
      :title="showDefaultRemoteEvents ? $t('agenda.hideRemoteEvents') : $t('agenda.showRemoteEvents')"
      icon
      :max-width="width"
      :max-height="height"
      @click="showRemoteEvents">
      <v-icon :size="size" :color="showDefaultRemoteEvents ? 'primary' : 'text-light-color'">
        fas fa-calendar-check
      </v-icon>
    </v-btn>
  </div>
</template>

<script>
export default {
  props: {
    size: {
      type: String,
      default: '18'
    },
    height: {
      type: String,
      default: '28'
    },
    width: {
      type: String,
      default: '28'
    },
    connectors: {
      type: Array,
      default: () => null,
    },
    settings: {
      type: Object,
      default: () => null,
    },
    showDefaultRemoteEvents: {
      type: Boolean,
      default: false,
    },
    showConnectAction: {
      type: Boolean,
      default: true,
    },
    showToggleAction: {
      type: Boolean,
      default: true,
    },
    showManageAction: {
      type: Boolean,
      default: false,
    },
  },


  computed: {
    /**
     * The two actions this button carries are distinct — connecting an account,
     * and showing or hiding remote events once one is connected — and they do
     * not belong in the same place. On a desktop personal agenda the left panel
     * offers connecting, beside the calendars it would fill, so the toolbar
     * shows only the toggle; where there is no panel the toolbar carries both.
     *
     * @returns {Boolean} true when this instance has something to render
     */
    showButton() {
      if (!this.connectors || !this.connectors.length) {
        return false;
      }
      if (this.connectedConnector) {
        return this.showToggleAction || (this.showManageAction && !this.caldavManaged);
      }
      return this.showConnectAction && !this.caldavManaged;
    },
    /**
     * Whether the instance chose this user's CalDAV server for them.
     *
     * Both actions this button offers when it is not toggling — connecting an
     * account, and managing the one connected — open the CalDAV-filtered
     * connectors drawer, so both are exactly the act managed mode takes away.
     * The show/hide-remote-events toggle is not: it is a view preference over
     * events that are already there, and a managed user keeps it. Suppressing
     * the whole button would have removed it too, from the one place that
     * offers it — the timeline header.
     *
     * @returns {Boolean} true when connect and manage must not be offered
     */
    caldavManaged() {
      return this.$remoteEventConnector.isCaldavManaged(this.connectors);
    },
    connectedConnector() {
      return this.connectors && this.connectors.find(connector => connector.connected);
    },
  },

  methods: {
    /**
     * Opens the shared connectors drawer on the CalDAV connectors alone. The
     * agenda application offers to connect the calendars that become the
     * user's own; adding a remote account to merely look at is a settings
     * act, done from the "Remote calendars" section.
     *
     * The filter is passed only when a CalDAV connector is recognisable —
     * without one there is nothing to narrow to, and narrowing anyway would
     * open an empty drawer instead of the legacy full list.
     *
     * @returns {void}
     */
    openPersonalCalendarDrawer() {
      const caldavKnown = this.connectors && this.connectors.some(connector => connector.isCaldav === true);
      this.$root.$emit('agenda-connectors-drawer-open', caldavKnown && {filter: 'caldav'} || null);
    },
    showRemoteEvents() {
      this.$root.$emit('agenda-show-remote-change',!this.showDefaultRemoteEvents);
    }
  },
};
</script>