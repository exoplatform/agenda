<template>
  <exo-drawer
    ref="agendaConnectorsDrawer"
    class="agendaConnectorsDrawer"
    body-classes="hide-scroll decrease-z-index-more"
    right>
    <template slot="title">
      {{ $t('agenda.connectYourPersonalAgenda') }}
    </template>
    <template slot="content">
      <v-list two-line>
        <v-list-item v-for="connector in connectors" :key="connector.name">
          <v-list-item-avatar>
            <v-avatar tile size="40">
              <img :alt="connector.name" :src="connector.avatar">
            </v-avatar>
          </v-list-item-avatar>
          <v-list-item-content>
            <template v-if="connector.user">
              <v-list-item-title>
                {{ $t('agenda.connectedAccountWith') }}:
              </v-list-item-title>
              <v-list-item-subtitle>
                {{ connector.user }}
              </v-list-item-subtitle>
            </template>
            <template v-else>
              <v-list-item-title class="title">
                {{ $t(connector.name) }}
              </v-list-item-title>
            </template>
          </v-list-item-content>
          <v-list-item-action>
            <v-btn
              v-if="connector.user"
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
        <v-card-text v-show="errorMessage" class="errorMessage">
          <v-alert type="error">
            {{ errorMessage }}
          </v-alert>
        </v-card-text>
      </v-list>
    </template>
  </exo-drawer>
</template>

<script>
export default {
  props: {
    connectedAccount: {
      type: Object,
      default: () => ({}),
    },
  },
  data: () => ({
    connectors: [],
    errorMessage: ''
  }),
  created() {
    this.$root.$on('agenda-connected-account-settings-open', this.open);
    // Retrieving list of registered connectors from extensionRegistry
    document.addEventListener('agenda-accounts-connectors-refresh', this.refreshConnectorsList);
    this.refreshConnectorsList();
  },
  methods: {
    refreshConnectorsList() {
      this.connectors = extensionRegistry.loadExtensions('agenda', 'connectors') || [];
    },
    open() {
      this.connectors.forEach(connector => {
        if (connector.init) {
          connector.init(this.connectionStatusChanged, this.connectionLoading);
        }
      });
      this.$refs.agendaConnectorsDrawer.open();
    },
    connectionLoading(connector, loading) {
      this.$set(connector, 'loading', loading);
      this.$forceUpdate();
    },
    connectionStatusChanged(connector, currentUser) {
      //if user has connected
      if (this.connectedAccount.userId) {
        connector.user = this.connectedAccount.userId;
      //if user disconnected from other browser
      } else if (!this.connectedAccount.userId && connector.isSignedIn) {
        this.disconnect(connector);
      } else {
        connector.user = currentUser && currentUser.user || null;
      }
    },
    connect(connector) {
      this.errorMessage = null;
      this.$refs.agendaConnectorsDrawer.startLoading();
      return connector.connect().then((connectorDetails) => {
        Object.assign(this.connectedAccount, {
          connectorName: connector.name,
          userId: connectorDetails.getBasicProfile().getEmail(),
          icon: connector.avatar
        });
        return this.$calendarService.saveAgendaConnectorsSettings(this.connectedAccount)
          .finally(() => {
            this.$refs.agendaConnectorsDrawer.endLoading();
          });
      }).catch(error => {
        console.error(`Error while connecting to your remote account: ${error.error}`);
        if(error.error !== 'popup_closed_by_user') {
          this.errorMessage = this.$t('agenda.connectionFailure');
        }
        this.connectionLoading(connector, false);
        this.$refs.agendaConnectorsDrawer.endLoading();
      });
    },
    disconnect(connector) {
      if (connector.isSignedIn) {
        connector.disconnect().then(() => {
          this.resetConnectedAccount();
        });
      } else {
        this.resetConnectedAccount(connector);
        this.connectionLoading(connector, true);
        this.connectionStatusChanged(connector, false);
      }
    },
    resetConnectedAccount(connector) {
      this.connectedAccount.userId = '';
      return this.$calendarService.saveAgendaConnectorsSettings(this.connectedAccount)
        .finally(() => {
          this.connectionLoading(connector, false);
        });
    }
  },
};
</script>

