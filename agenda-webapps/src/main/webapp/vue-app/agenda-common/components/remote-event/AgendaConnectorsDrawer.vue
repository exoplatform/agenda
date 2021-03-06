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
        <v-list-item
          v-for="connector in enabledConnectors"
          :key="connector.name">
          <v-list-item-avatar>
            <v-avatar tile size="40">
              <img :src="connector.avatar">
            </v-avatar>
          </v-list-item-avatar>
          <v-list-item-content>
            <v-alert
              v-if="!connector.canConnect"
              type="error"
              class="my-auto">
              {{ $t('agenda.connectoInitializationFailed') }}
            </v-alert>
            <template v-else-if="connector.connected">
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
              <span class="my-auto ms-auto pt-4 pe-2">
                <i class="uiIconColorWarning"></i>
              </span>
              <span class="my-auto me-auto">
                {{ $t('agenda.allowedToConnectOnlyOneConnector') }}
              </span>
            </div>
          </v-list-item-content>
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
        <p>{{ $t('agenda.noActiveConnectors') }}</p>
      </div>
    </template>
  </exo-drawer>
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
    errorMessage: '',
  }),
  computed: {
    enabledConnectors() {
      return this.connectors && this.connectors.slice().filter(connector => connector.enabled) || [];
    }
  },
  created() {
    this.$root.$on('agenda-connectors-drawer-open', this.open);
    this.$root.$on('agenda-connector-connected', () => {
      // Avoiding closing the drawer automatically
      // when the user didn't pressed the connect button
      if (this.connectionInProgress) {
        this.close();
      }
    });
  },
  methods: {
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
      this.$root.$emit('agenda-connector-connect', connector);
    },
    disconnect(connector) {
      this.connectionInProgress = true;
      this.$root.$emit('agenda-connector-disconnect', connector);
    },
  },
};
</script>

