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
      <v-list
        v-if="enabledConnectors && enabledConnectors.length !== 0"
        two-line>
        <v-list-item v-for="connector in enabledConnectors" :key="connector.name">
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
      <div
        v-else
        class="noEnabledConnectors d-flex flex-column align-center">
        <i class="uiIconCalRemoteCalendar darkGreyIcon ma-5"></i>
        <p>{{ $t('agenda.disabled.connectors') }}</p>
      </div>
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
    connectors: {
      type: Array,
      default: () => [],
    },
  },
  data: () => ({
    errorMessage: ''
  }),
  computed: {
    enabledConnectors() {
      return this.connectors.slice().filter(connector => connector.enabled);
    }
  },
  created() {
    this.$root.$on('agenda-connected-account-settings-open', this.open);
  },
  methods: {
    open() {
      this.$root.$emit('agenda-init-connectors');
      this.$refs.agendaConnectorsDrawer.open();
    },
    connect(connector) {
      this.errorMessage = null;
      this.$refs.agendaConnectorsDrawer.startLoading();
      connector.connect().then((connectorDetails) => {
        Object.assign(this.connectedAccount, {
          connectorName: connector.name,
          userId: connectorDetails.getBasicProfile().getEmail(),
          icon: connector.avatar
        });
        this.$refs.agendaConnectorsDrawer.close();
        this.$settingsService.setSettingsValue('USER',eXo.env.portal.userName,'APPLICATION','Agenda','agendaConnectorsSettings',this.connectedAccount)
          .finally(() => {
            this.$refs.agendaConnectorsDrawer.endLoading();
          });
      }).catch(error => {
        console.error(`Error while connecting to your remote account: ${error.error}`);
        if(error.error !== 'popup_closed_by_user') {
          this.errorMessage = this.$t('agenda.connectionFailure');
        }
        this.$set(connector, 'loading', false);
        this.$refs.agendaConnectorsDrawer.endLoading();
      });
    },
    disconnect(connector) {
      //disconnect from connected browser
      if (connector.isSignedIn) {
        connector.disconnect().then(() => {
          this.resetConnectedAccount(connector);
        });
      } else {//disconnect from other browser
        this.$set(connector, 'loading', true);
        this.resetConnectedAccount(connector);
        this.$root.$emit('agenda-synchronize-current-connector', connector, false);
      }
    },
    resetConnectedAccount(connector) {
      this.connectedAccount.userId = '';
      return this.$settingsService.setSettingsValue('USER', eXo.env.portal.userName, 'APPLICATION', 'Agenda', 'agendaConnectorsSettings', this.connectedAccount)
        .finally(() => {
          this.$set(connector, 'loading', false);
        });
    }
  },
};
</script>

