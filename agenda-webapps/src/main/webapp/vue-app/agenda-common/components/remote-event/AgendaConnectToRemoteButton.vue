<template>
  <div v-if="showButton" class="d-flex align-center">
    <v-btn
      v-if="!connectedConnector && connectOffered"
      :title="$t('agenda.connectYourPersonalAgenda')"
      :loading="!!(caldavManaged && managedConnector && managedConnector.loading)"
      icon
      :max-width="width"
      :max-height="height" 
      @click="connectPersonalCalendar">
      <v-icon :size="size" class="text-light-color">
        fas fa-plug
      </v-icon>
    </v-btn>
    <!--
      Managing the connection opens the connectors drawer, which managed mode
      takes away (EXO-90836): a managed user gets, in the same place, the one
      thing the drawer still had for them, synchronising now (EXO-91030).
    -->
    <v-btn
      v-else-if="connectedConnector && showManageAction && caldavManaged && syncableConnector"
      :title="$t('agenda.connectors.syncNow')"
      :aria-label="$t('agenda.connectors.syncNow')"
      :loading="syncing"
      :disabled="syncing"
      icon
      :max-width="width"
      :max-height="height"
      @click="syncNow">
      <v-icon :size="size" class="text-light-color">
        fas fa-sync-alt
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


  data: () => ({
    syncing: false,
  }),
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
        return this.showToggleAction || (this.showManageAction && (!this.caldavManaged || !!this.syncableConnector));
      }
      return this.connectOffered;
    },
    /**
     * Whether connecting is offered: the connectors drawer, or, to a user
     * managed mode governs, the designated server in one click (EXO-90836) -
     * and nothing when no descriptor says which server it is.
     *
     * @returns {Boolean} true when the plug connects something
     */
    connectOffered() {
      return this.showConnectAction && (!this.caldavManaged || !!this.managedConnector);
    },
    managedConnector() {
      return this.$remoteEventConnector.managedCaldavConnector(this.connectors);
    },
    /**
     * Whether the instance chose this user's CalDAV server for them.
     *
     * Managing the connected account opens the CalDAV-filtered connectors
     * drawer, which managed mode takes away, and a sync button takes its place;
     * connecting connects the designated server in one click instead of
     * opening it.
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
    /**
     * The connected CalDAV account a managed user synchronises from here, when
     * its connector can be asked to synchronise on demand.
     *
     * @returns {Object} the connector, null when there is none
     */
    syncableConnector() {
      return (this.connectors || []).find(connector => connector
        && connector.isCaldav === true
        && connector.connected
        && typeof connector.sync === 'function') || null;
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
    /**
     * Connects the user's own calendars: the designated server in one click when
     * managed mode governs them, the connectors drawer otherwise.
     *
     * @returns {void}
     */
    connectPersonalCalendar() {
      if (this.caldavManaged) {
        this.$root.$emit('agenda-connector-connect', this.managedConnector);
      } else {
        this.openPersonalCalendarDrawer();
      }
    },
    /**
     * Synchronises the managed user's CalDAV account now, as the connectors
     * drawer and the settings do, then refreshes the agenda.
     *
     * @returns {Promise} resolves once the synchronisation has run
     */
    syncNow() {
      const connector = this.syncableConnector;
      this.syncing = true;
      return Promise.resolve(connector.sync())
        .then(() => this.$root.$emit('agenda-refresh'))
        .catch(error => {
          console.error('cannot synchronise the connected account', error);
          this.$root.$emit('alert-message', this.$t('agenda.connectors.syncError'), 'error');
        })
        .finally(() => this.syncing = false);
    },
    showRemoteEvents() {
      this.$root.$emit('agenda-show-remote-change',!this.showDefaultRemoteEvents);
    }
  },
};
</script>