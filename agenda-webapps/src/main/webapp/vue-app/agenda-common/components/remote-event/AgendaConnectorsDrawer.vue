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
              <v-list-item-subtitle
                v-else-if="connectorSubtitle(connector)"
                :title="connectorSubtitle(connector)">
                {{ connectorSubtitle(connector) }}
              </v-list-item-subtitle>
            </v-list-item-content>
            <v-list-item-action v-if="connector.canConnect">
              <v-btn
                v-if="connector.isSignedIn && connector.user"
                :loading="connector.loading"
                class="btn"
                @click="disconnect(connector)">
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
          <v-list-item>
            <v-list-item-content>
              <div class="d-flex">
                <span class="my-auto pe-4">
                  <v-icon
                    size="16"
                    class="text-light-color"
                    depressed>
                    fa-info-circle
                  </v-icon>
                </span>
                <span class="my-auto me-auto text-subtitle">
                  {{ $t('agenda.allowedToConnectOnlyOneConnector') }}
                </span>
              </div>
            </v-list-item-content>
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
    <exo-confirm-dialog
      ref="confirmConnectDialog"
      :title="confirmConnectDialogLabels.title"
      :message="confirmConnectDialogLabels.message"
      :ok-label="confirmConnectDialogLabels.ok"
      :cancel-label="confirmConnectDialogLabels.cancel"
      @ok="confirmConnect" />
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
    selectedConnector: null
  }),
  computed: {
    enabledConnectors() {
      return this.connectors && this.connectors.slice().filter(connector => connector.enabled) || [];
    },
    confirmConnectDialogLabels() {
      return {
        title: this.$t('agenda.agendaConnectors.confirmConnectDialog.title'),
        message: this.$t('agenda.agendaConnectors.confirmConnectDialog.message'),
        ok: this.$t('agenda.agendaConnectors.confirmConnectDialog.ok'),
        cancel: this.$t('agenda.agendaConnectors.confirmConnectDialog.cancel')
      };
    },
  },
  created() {
    this.$root.$on('agenda-connectors-drawer-open', this.open);
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
    connectorSubtitle(connector) {
      if (!connector.description) {
        return '';
      }
      const translation = this.$t(connector.description);
      return translation === connector.description ? '' : translation;
    },
    open() {
      this.connectionInProgress = false;
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
    connect(connector) {
      this.connectionInProgress = true;
      this.selectedConnector = connector;
      if (this.enabledConnectors.some(c => c.isSignedIn && c.user)) {
        this.$refs.confirmConnectDialog.open();
      }
      else {
        this.confirmConnect();
      }
    },
    confirmConnect() {
      this.$root.$emit('agenda-connector-connect', this.selectedConnector);
    },
    disconnect(connector) {
      this.connectionInProgress = true;
      this.$root.$emit('agenda-connector-disconnect', connector);
    },
  },
};
</script>
