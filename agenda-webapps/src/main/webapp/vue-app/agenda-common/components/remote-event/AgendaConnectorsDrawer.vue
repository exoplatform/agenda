<template>
  <div>
    <exo-drawer
      ref="agendaConnectorsDrawer"
      class="agendaConnectorsDrawer"
      body-classes="hide-scroll decrease-z-index-more"
      right>
      <template slot="title">
        {{ $t('agenda.connectYourPersonalAgenda') }}
      </template>
      <template slot="content">
        <v-list
          v-if="enabledConnectors && enabledConnectors.length !== 0"
          two-line>
          <v-list-item
            v-for="connector in enabledConnectors"
            :key="connector.name">
            <v-list-item-avatar class="rounded-0">
              <!-- The identity the administrator configured, resolved by the
                   shared connector avatar: uploaded image, else chosen font
                   icon, else the connector's packaged avatar — the same rule
                   the admin screens apply. -->
              <agenda-connector-avatar
                :connector="connector"
                size="40" />
            </v-list-item-avatar>
            <v-list-item-content>
              <!-- The row is ALWAYS titled by the connector's name: with
                   several servers of one kind declared, the name is the only
                   thing telling them apart, so no state may replace it. -->
              <v-list-item-title>
                {{ $t(connector.name) }}
              </v-list-item-title>
              <v-alert
                v-if="!connector.canConnect"
                type="error"
                dense
                text
                class="my-auto">
                {{ $t('agenda.connectoInitializationFailed') }}
              </v-alert>
              <v-list-item-subtitle
                v-else-if="connector.connected"
                :title="`${$t('agenda.connectedAccountWith')}: ${connector.user}`"
                class="d-flex align-center">
                <v-icon
                  size="12"
                  color="success"
                  class="me-1">
                  fa-check-circle
                </v-icon>
                <span class="text-truncate">
                  {{ connector.user }}
                </span>
              </v-list-item-subtitle>
              <!--
                The state that makes the action beside it worth pressing: four
                minutes means there is nothing to do, two hours is a reason.
                Only for a connector that can answer the question.
              -->
              <v-list-item-subtitle
                v-if="connector.connected && lastSyncLabel(connector)"
                class="text-truncate">
                {{ lastSyncLabel(connector) }}
              </v-list-item-subtitle>
              <v-list-item-subtitle
                v-else-if="connectorSubtitle(connector)"
                :title="connectorSubtitle(connector)">
                {{ connectorSubtitle(connector) }}
              </v-list-item-subtitle>
            </v-list-item-content>
            <!--
              A row, not the column v-list-item-action lays out by default: a
              connected account can carry two actions, and stacked they
              overlap the row above.
            -->
            <v-list-item-action v-if="connector.canConnect" class="d-flex flex-row align-center">
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
                <v-icon size="18">fa-sync-alt</v-icon>
              </v-btn>
              <v-btn
                v-if="connector.isSignedIn && connector.user"
                :loading="connector.loading"
                class="btn"
                @click="askBeforeDisconnecting(connector)">
                {{ $t('agenda.disconnect') }}
              </v-btn>
              <v-btn
                v-else
                :loading="connector.loading"
                class="btn"
                @click="connect(connector)">
                {{ $t('agenda.connect') }}
              </v-btn>
            </v-list-item-action>
          </v-list-item>
        </v-list>
        <div
          v-else
          class="noEnabledConnectors d-flex flex-column align-center">
          <i class="uiIconCalRemoteCalendar darkGreyIcon ma-5"></i>
          <p>{{ $t('agenda.noActiveConnectors') }}</p>
        </div>
      </template>
    </exo-drawer>
    <div id="agendaConnectorSettingsDrawer"></div>
    <!--
      A connector may have something to say before it is unlinked. Only it
      knows what unlinking costs on its side, so it supplies the sentence and
      this shows it; a connector with nothing to say is disconnected straight
      away, as before.
    -->
    <exo-confirm-dialog
      ref="confirmDisconnectDialog"
      :title="$t('agenda.connectors.disconnect.confirmTitle')"
      :message="disconnectWarning"
      :ok-label="$t('agenda.connectors.disconnect.confirm')"
      :cancel-label="$t('agenda.connectors.disconnect.cancel')"
      @ok="confirmDisconnect" />
  </div>
</template>

<script>
export default {
  props: {
    connectors: {
      type: Array,
      default: () => [],
    },
  },
  data: () => ({
    connectionInProgress: false,
    syncing: null,
    lastSyncs: {},
    disconnectWarning: '',
    connectorToDisconnect: null,
    connectorFilter: null,
  }),
  computed: {
    /**
     * The connectors this opening of the drawer offers. Beyond the
     * administrator's enablement, the opener may have asked for one family
     * only: the settings' "Your calendars" section connects the CalDAV
     * account backing My Calendars, its "Remote calendars" section adds
     * accounts on everything else. The flag is the connector descriptor's
     * declared `isCaldav` constant; a descriptor shipped before the constant
     * existed simply counts as not CalDAV.
     *
     * @returns {Array} the connectors to list
     */
    enabledConnectors() {
      const enabledConnectors = this.connectors && this.connectors.slice().filter(connector => connector.enabled) || [];
      if (this.connectorFilter === 'caldav') {
        return enabledConnectors.filter(connector => connector.isCaldav === true);
      } else if (this.connectorFilter === 'remote') {
        return enabledConnectors.filter(connector => connector.isCaldav !== true);
      }
      return enabledConnectors;
    },
  },
  created() {
    this.$root.$on('agenda-connectors-drawer-open', this.open);
    // Read on open rather than once at creation: the drawer outlives several
    // synchronisations, and a line stamped when the page loaded would age
    // silently while the user looks at it.
    this.$root.$on('agenda-connectors-drawer-open', this.retrieveLastSyncs);
    // The calendar step opens on top of this drawer and is the last thing the
    // user does when connecting; once it is done there is nothing left here to
    // come back to, so this closes with it rather than being revealed again.
    this.$root.$on('agenda-connector-mirror-calendar-done', this.close);
    this.$root.$on('agenda-connector-connected', () => {
      // Avoiding closing the drawer automatically
      // when the user didn't pressed the connect button
      if (this.connectionInProgress) {
        this.close();
      }
    });
  },
  methods: {
    /**
     * The secondary line of a connector row: the connector's translated
     * description — for a declared CalDAV server, what the administrator
     * typed, falling back to the server's host, both merged into the shared
     * i18n instance under the descriptor's description key. A descriptor
     * without a description, or whose key never got a translation, yields
     * nothing so the row stays a clean single line rather than showing a raw
     * key.
     *
     * @param {Object} connector the connector descriptor of the row
     * @returns {String} the resolved secondary line, empty when there is none
     */
    /**
     * Whether this connector can be asked to synchronise on demand.
     *
     * Declared by the connector, not assumed: an OAuth calendar whose events
     * arrive by push has nothing to run, and offering a button that does
     * nothing is worse than offering none.
     *
     * @param {Object} connector the connector descriptor of the row
     * @returns {Boolean} true when the row shows a Sync now button
     */
    canSync(connector) {
      return !!(connector && connector.connected && typeof connector.sync === 'function');
    },
    /**
     * When this connector last finished synchronising, in words.
     *
     * The phrasing is shared with the settings row, so the two cannot come to
     * disagree about what "just now" means.
     *
     * @param {Object} connector the connector descriptor of the row
     * @returns {String} the line to display, empty when unknown
     */
    lastSyncLabel(connector) {
      // Not yet read and never synchronised are different answers: the first
      // has nothing to say, the second has something the user should see.
      if (!Object.prototype.hasOwnProperty.call(this.lastSyncs, connector.name)) {
        return '';
      }
      const phrase = this.$remoteEventConnector.lastSyncPhrase(this.lastSyncs[connector.name]);
      return phrase && this.$t(phrase.key, {0: phrase.count}) || '';
    },
    /**
     * Reads the last synchronisation of every connector able to report one.
     *
     * A connector that fails to answer is left without a line rather than
     * given a wrong one: not knowing when the last sync happened is a smaller
     * problem than stating a time that is not true.
     *
     * @returns {Promise} resolves once every connector has answered or failed
     */
    retrieveLastSyncs() {
      return Promise.all(this.enabledConnectors
        .filter(connector => connector.connected && typeof connector.lastSynchronised === 'function')
        .map(connector => Promise.resolve(connector.lastSynchronised())
          .then(lastSync => this.$set(this.lastSyncs, connector.name, lastSync || null))
          .catch(error => console.error('cannot read when the account last synchronised', error))));
    },
    /**
     * Synchronises one connector's account now.
     *
     * One at a time, and the state is read again afterwards: pressing the
     * button and seeing the line stay where it was is the one outcome that
     * would make the button look broken.
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
    connectorSubtitle(connector) {
      if (!connector.description) {
        return '';
      }
      const translation = this.$t(connector.description);
      return translation === connector.description ? '' : translation;
    },
    /**
     * Opens the drawer, optionally scoped to one connector family.
     *
     * @param {Object} options when given, `{filter: 'caldav'|'remote'}`
     *          restricts the rows to that family; absent, every enabled
     *          connector is offered
     * @returns {void}
     */
    open(options) {
      this.connectionInProgress = false;
      this.connectorFilter = options && options.filter || null;
      this.$root.$emit('agenda-connectors-init');
      if (this.$refs.agendaConnectorsDrawer) {
        this.$refs.agendaConnectorsDrawer.open();
      }
    },
    close() {
      if (this.$refs.agendaConnectorsDrawer) {
        this.$refs.agendaConnectorsDrawer.close();
      }
    },
    /**
     * Connects an account on this connector. No confirmation and no eviction:
     * accounts are additive now — connecting Google leaves the CalDAV account
     * standing, and vice versa — so there is no other account to warn about
     * replacing.
     *
     * @param {Object} connector the connector to connect an account on
     * @returns {void}
     */
    connect(connector) {
      this.connectionInProgress = true;
      this.$root.$emit('agenda-connector-connect', connector);
    },
    /**
     * Asks the connector what unlinking it costs, and confirms when it has an
     * answer.
     *
     * A connector that declares nothing is disconnected straight away — that
     * was the behaviour for every connector until one of them started
     * removing things on the way out, and it stays right for the others.
     *
     * @param {Object} connector the connector to unlink
     * @returns {Promise} resolves once the dialog is up, or the account gone
     */
    askBeforeDisconnecting(connector) {
      if (typeof connector.disconnectWarning !== 'function') {
        // A connector that never offered to explain the cost is left as it
        // was. Giving one a confirmation it was not written for is a change
        // to that connector's behaviour, not a fix to this one's.
        return Promise.resolve(this.disconnect(connector));
      }
      this.connectorToDisconnect = connector;
      return Promise.resolve(connector.disconnectWarning())
        .then(warning => this.confirmDisconnecting(warning))
        .catch(error => {
          console.error('cannot read what disconnecting this account costs', error);
          return this.confirmDisconnecting('');
        });
    },
    /**
     * Opens the confirmation, whatever the connector managed to say.
     *
     * <p>
     * Always opens it. A connector that offers to explain the cost of
     * disconnecting is one where disconnecting costs something, and that does
     * not stop being true when the explanation is unavailable — a locale
     * without the string, a stale bundle, a request that failed. Skipping the
     * dialog then turned a missing translation into one click that silently
     * removed every calendar the account had materialised.
     *
     * <p>
     * Failing open costs nothing here: the dialog is a question, and a user
     * who still wants to disconnect answers it. Failing closed cost data.
     *
     * @param {String} warning what the connector said, possibly empty
     * @returns {Promise} resolves once the dialog is up
     */
    confirmDisconnecting(warning) {
      this.disconnectWarning = warning || this.$t('agenda.connectors.disconnect.genericWarning');
      return this.$refs.confirmDisconnectDialog.open();
    },
    /**
     * Unlinks the connector the dialog was opened for.
     *
     * @returns {Promise} resolves once the account is gone
     */
    confirmDisconnect() {
      return Promise.resolve(this.disconnect(this.connectorToDisconnect));
    },
    disconnect(connector) {
      this.connectionInProgress = true;
      this.$root.$emit('agenda-connector-disconnect', connector);
    },
  },
};
</script>
