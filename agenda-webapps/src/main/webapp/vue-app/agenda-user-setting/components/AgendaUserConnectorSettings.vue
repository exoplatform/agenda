<template>
  <v-list-item>
    <v-list-item-content>
      <v-list-item-title class="title text-color">
        <div>
          {{ $t('agenda.connectYourPersonalAgenda') }}
        </div>
      </v-list-item-title>
      <v-list-item-subtitle class="my-3 text-sub-title font-italic">
        <agenda-connector-status
          :connected-account="connectedAccount"
          :is-connected-connector-enabled="isConnectedConnectorEnabled">
          <template slot="connectButton">
            {{ $t('agenda.connectYourPersonalAgendaSubTitle') }}
          </template>
        </agenda-connector-status>
      </v-list-item-subtitle>
    </v-list-item-content>
    <v-list-item-action>
      <v-btn
        small
        icon
        @click="openDrawer">
        <i class="uiIconEdit uiIconLightBlue pb-2"></i>
      </v-btn>
    </v-list-item-action>
    <agenda-user-connected-account-drawer
      :connected-account="connectedAccount"
      :connectors="connectors" />
    <agenda-connector :settings="settings" />
  </v-list-item>
</template>

<script>
export default {
  props: {
    settings: {
      type: Object,
      default: () => null,
    },
  },
  data: () => ({
    connectedAccount: {},
    connectors: [],
  }),
  computed: {
    remoteProviders() {
      return this.settings && this.settings.remoteProviders;
    },
    connectedAccountName() {
      return this.connectedAccount && this.connectedAccount.userId || '';
    },
    connectedAccountIconSource() {
      return this.connectedAccount && this.connectedAccount.icon || '';
    },
    connectedConnector() {
      return this.connectors.find(connector => connector.name === this.connectedAccount.connectorName);
    },
    isConnectedConnectorEnabled() {
      return this.connectedConnector && this.connectedConnector.enabled;
    }
  },
  watch: {
    remoteProviders() {
      if (this.remoteProviders) {
        this.refresh();
      }
    },
  },
  created() {
    this.$root.$on('agenda-refresh', this.refresh);
    this.$root.$on('agenda-connector-loaded', connectors => {
      this.connectors = connectors;
      this.refresh();
    });
    this.$root.$on('agenda-connector-initialized', connectors => {
      this.connectors = connectors;
      const connectorObj = this.connectors && this.connectors.find(connector => connector.name === this.connectedAccount.connectorName);
      if (connectorObj) {
        this.connectedAccount.icon = connectorObj.avatar;
        this.connectedAccount.connectorName = connectorObj.name;
        this.connectedAccount.userId = connectorObj.user;
      } else {
        this.connectedAccount = {};
      }
    });
    this.$root.$emit('agenda-init-connectors');
  },
  methods: {
    refresh() {
      const connectorObj = this.connectors && this.connectors.find(connector => connector.name === this.connectedAccount.connectorName);
      if (connectorObj) {
        this.connectedAccount = {
          connectorName: this.settings && this.settings.connectedRemoteProvider,
          userId: this.settings && this.settings.connectedRemoteUserId,
          icon: connectorObj.avatar
        };
      } else {
        this.connectedAccount = {
          connectorName: this.settings && this.settings.connectedRemoteProvider,
          userId: this.settings && this.settings.connectedRemoteUserId,
        };
      }
    },
    openDrawer() {
      this.$root.$emit('agenda-connected-account-settings-open');
    },
    getDayFromAbbreviation(day) {
      return this.$agendaUtils.getDayNameFromDayAbbreviation(day, eXo.env.portal.language);
    },
  }
};
</script>