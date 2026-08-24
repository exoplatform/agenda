<template>
  <div v-if="displayed">
    <!--
      The "Remote calendars" section: the additive list of non-CalDAV accounts
      (Google, Office 365). Separate from "Your calendars" above because they
      are different things to the user: those ARE their eXo calendars, these
      are outside accounts kept beside them — several can be connected at
      once, one per provider.
    -->
    <v-list-item>
      <v-list-item-content>
        <!-- text-color, the class the E-mail rows on this same page use, so
             the section header reads like its siblings. -->
        <v-list-item-title class="text-color">
          {{ $t('agenda.settings.remoteCalendars') }}
        </v-list-item-title>
        <v-list-item-subtitle>
          {{ $t('agenda.settings.remoteCalendarsSubTitle') }}
        </v-list-item-subtitle>
      </v-list-item-content>
      <v-list-item-action>
        <v-btn
          :aria-label="$t('agenda.settings.remoteCalendarsAdd')"
          class="btn"
          @click="openDrawer">
          <v-icon size="14" class="me-1">fa-plus</v-icon>
          {{ $t('agenda.settings.remoteCalendarsAdd') }}
        </v-btn>
      </v-list-item-action>
    </v-list-item>
    <!--
      One row per connected account, each carrying its own state and actions:
      with several accounts held at once, "the connected account" stops being
      one thing, so nothing here may speak for all of them.
    -->
    <v-list-item
      v-for="connector in remoteConnectedConnectors"
      :key="connector.name"
      class="ps-8">
      <v-list-item-avatar class="rounded-0" size="28">
        <agenda-connector-avatar
          :connector="connector"
          size="28" />
      </v-list-item-avatar>
      <v-list-item-content>
        <v-list-item-title>
          {{ $t(connector.name) }}
        </v-list-item-title>
        <v-list-item-subtitle class="d-flex align-center">
          <span class="text-truncate">
            {{ connector.user }}
          </span>
          <span v-if="lastSyncLabel(connector)" class="ms-1 text-truncate">
            · {{ lastSyncLabel(connector) }}
          </span>
        </v-list-item-subtitle>
        <!--
          The per-account opt-out from receiving copies of the user's
          meetings: copies go to every connected account able to take them,
          and this switch is how one account is excluded without silencing
          the others. Only shown on an account that can receive copies at
          all — a switch on one that cannot would promise nothing.
        -->
        <v-list-item-subtitle v-if="connector.canPush" class="d-flex align-center">
          <v-switch
            :input-value="connector.pushEnabled !== false"
            :loading="savingPushFor === connector.name"
            :disabled="savingPushFor !== null"
            dense
            hide-details
            class="mt-0 me-2 my-auto"
            @change="savePushOptOut(connector, $event)" />
          <span class="text-subtitle my-auto">
            {{ $t('agenda.settings.remoteCalendarsReceiveCopies') }}
          </span>
        </v-list-item-subtitle>
      </v-list-item-content>
      <v-list-item-action class="d-flex flex-row align-center">
        <v-btn
          v-if="canSync(connector)"
          :loading="syncing === connector.name"
          :disabled="syncing !== null"
          :aria-label="$t('agenda.connectors.syncNow')"
          :title="$t('agenda.connectors.syncNow')"
          icon
          small
          class="me-2"
          @click="syncNow(connector)">
          <v-icon size="18" class="icon-default-color">fa-sync-alt</v-icon>
        </v-btn>
        <v-btn
          :loading="connector.loading"
          class="btn"
          @click="disconnect(connector)">
          {{ $t('agenda.disconnect') }}
        </v-btn>
      </v-list-item-action>
    </v-list-item>
  </div>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => null,
    },
    connectors: {
      type: Array,
      default: () => [],
    },
  },
  data: () => ({
    syncing: null,
    savingPushFor: null,
    lastSyncs: {},
  }),
  computed: {
    /**
     * Whether the section is worth showing: only a deployment offering at
     * least one enabled non-CalDAV connector has remote accounts to add.
     *
     * @returns {Boolean} true when the section renders
     */
    displayed() {
      return this.remoteConnectors.length > 0;
    },
    /**
     * The enabled connectors this section offers: everything that is not the
     * CalDAV connector backing My Calendars. A descriptor shipped before the
     * `isCaldav` constant existed counts as remote — the defensive reading,
     * so no connector becomes unreachable while the flag lands.
     *
     * @returns {Array} the enabled non-CalDAV connectors
     */
    remoteConnectors() {
      return (this.connectors || [])
        .filter(connector => connector && connector.enabled && connector.isCaldav !== true);
    },
    /**
     * The remote accounts currently connected, one row each.
     *
     * @returns {Array} the connected non-CalDAV connectors
     */
    remoteConnectedConnectors() {
      return this.remoteConnectors.filter(connector => connector.connected);
    },
  },
  watch: {
    /**
     * Reads the accounts' synchronisation state whenever the set of connected
     * accounts changes, so a freshly connected row shows its line without a
     * reload.
     * @returns {void}
     */
    remoteConnectedConnectors() {
      this.retrieveLastSyncs();
    },
  },
  created() {
    this.retrieveLastSyncs();
  },
  methods: {
    /**
     * Opens the connect drawer scoped to remote connectors: adding an account
     * here must never offer the CalDAV row, whose place is the
     * "Your calendars" section.
     *
     * @returns {void}
     */
    openDrawer() {
      this.$root.$emit('agenda-connectors-drawer-open', {filter: 'remote'});
    },
    /**
     * Whether this account can be asked to synchronise on demand — declared
     * by the connector, not assumed.
     *
     * @param {Object} connector the connector of the row
     * @returns {Boolean} true when the row shows a Sync now button
     */
    canSync(connector) {
      return !!(connector && connector.connected && typeof connector.sync === 'function');
    },
    /**
     * When this account last finished synchronising, in words — the phrasing
     * shared with the other account rows, so they cannot disagree about what
     * "just now" means.
     *
     * @param {Object} connector the connector of the row
     * @returns {String} the line to display, empty when unknown
     */
    lastSyncLabel(connector) {
      if (!Object.prototype.hasOwnProperty.call(this.lastSyncs, connector.name)) {
        return '';
      }
      const phrase = this.$remoteEventConnector.lastSyncPhrase(this.lastSyncs[connector.name]);
      return phrase && this.$t(phrase.key, {0: phrase.count}) || '';
    },
    /**
     * Reads the last synchronisation of every connected account able to
     * report one. A connector that fails to answer is left without a line
     * rather than given a wrong one.
     *
     * @returns {Promise} resolves once every connector has answered or failed
     */
    retrieveLastSyncs() {
      return Promise.all(this.remoteConnectedConnectors
        .filter(connector => typeof connector.lastSynchronised === 'function')
        .map(connector => Promise.resolve(connector.lastSynchronised())
          .then(lastSync => this.$set(this.lastSyncs, connector.name, lastSync || null))
          .catch(error => console.error('cannot read when the account last synchronised', error))));
    },
    /**
     * Synchronises one account now, one at a time, then reads its state
     * again: pressing the button and seeing the line stay where it was is the
     * one outcome that would make it look broken.
     *
     * @param {Object} connector the connector to synchronise
     * @returns {Promise} resolves once the synchronisation has run
     */
    syncNow(connector) {
      this.syncing = connector.name;
      return Promise.resolve(connector.sync())
        .then(() => {
          this.$root.$emit('agenda-refresh');
          return this.retrieveLastSyncs();
        })
        .catch(error => {
          console.error('cannot synchronise the connected account', error);
          this.$root.$emit('alert-message', this.$t('agenda.connectors.syncError'), 'error');
        })
        .finally(() => this.syncing = null);
    },
    /**
     * Disconnects this account only: the shared connector component removes
     * the one named account and leaves the others — including the CalDAV
     * account backing My Calendars — standing.
     *
     * @param {Object} connector the connector to disconnect
     * @returns {void}
     */
    disconnect(connector) {
      this.$root.$emit('agenda-connector-disconnect', connector);
    },
    /**
     * Stores this account's copy opt-out, and puts the switch back when the
     * save fails so it never shows a state the server does not hold. The
     * setting is per account: the stored entry for this provider is what
     * changes, never a global flag that would silence the other accounts.
     *
     * @param {Object} connector the connector of the row
     * @param {Boolean} enabled whether the account receives copies
     * @returns {Promise} resolves once the setting is stored
     */
    savePushOptOut(connector, enabled) {
      const accounts = this.settings && this.settings.connectedConnectors || [];
      const account = accounts.find(connectedAccount => connectedAccount.providerName === connector.name);
      if (!account) {
        return Promise.resolve();
      }
      const previousValue = account.pushEnabled !== false;
      account.pushEnabled = !!enabled;
      this.savingPushFor = connector.name;
      return this.$settingsService.saveUserSettings(this.settings)
        .then(() => this.$root.$emit('agenda-settings-refresh'))
        .catch(error => {
          console.error('cannot store the account copy setting', error);
          account.pushEnabled = previousValue;
          this.$root.$emit('alert-message', this.$t('agenda.settings.pushEventsError'), 'error');
        })
        .finally(() => this.savingPushFor = null);
    },
  },
};
</script>
